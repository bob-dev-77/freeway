# freeway-db 改进方案

> 基于 db-review.md 评估的问题，逐一给出具体修复方案。
> 目标：保持零外部依赖哲学，最小侵入性修改，解决生产环境隐患。

---

## 🔴 Critical 修复

### C1. 无查询超时 → 新增 Statement 超时机制

**问题**：`PreparedStatement` 未调用 `setQueryTimeout()`，慢 SQL 可永久阻塞线程。

**方案**：在 `QueryImpl` 和 `BatchQueryImpl` 的连接借用阶段配置超时。

**修改点**：

**QueryImpl.borrow()** — 超时从配置注入：

```java
private ExecuteContext borrow() throws SQLException {
    var connSource = transactionConnection != null
        ? transactionConnection
        : db.pool.borrow();
    var stmt = connSource.jdbcConnection()
        .prepareStatement(jdbcSql(), Statement.NO_GENERATED_KEYS);
    stmt.setQueryTimeout(db.queryTimeoutSeconds);  // ← 新增
    var pool = transactionConnection != null ? null : db.pool;
    return new ExecuteContext(stmt, transactionConnection != null ? null : connSource, pool);
}
```

**DatabaseImpl** — 构造时接收超时配置：

```java
public class DatabaseImpl implements Database {
    final ConnectionPool pool;
    final DefaultRowMapper rowMapper;
    final int queryTimeoutSeconds;  // ← 新增

    public DatabaseImpl(ConnectionPool pool, int queryTimeoutSeconds) {
        this.pool = pool;
        this.rowMapper = new DefaultRowMapper();
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }
}
```

**DatabaseBuilder** — 新增配置接口：

```java
public class DatabaseBuilder {
    Duration queryTimeout = Duration.ofSeconds(30);  // ← 默认30秒

    public DatabaseBuilder queryTimeout(Duration timeout) {
        this.queryTimeout = timeout;
        return this;
    }

    public Database build() {
        // ...
        return new DatabaseImpl(pool, rowMapper, (int) queryTimeout.toSeconds());
    }
}
```

**DbModule** — 新增符号配置：

```java
@Contribute(SymbolProvider.class) @FactoryDefaults
public static void setupDefaultSymbols(MappedConfiguration<String, Object> config) {
    config.add("freeway.db.query-timeout", "30 s");
}

public static Database buildDatabase(
    @Symbol("freeway.db.query-timeout") @IntermediateType(Duration.class) int queryTimeout,
    // ... 其他参数
) {
    return Database.builder()
        .queryTimeout(Duration.ofSeconds(queryTimeout))
        // ...
        .build();
}
```

**涉及文件**：`QueryImpl.java`, `BatchQueryImpl.java`, `DatabaseImpl.java`, `DatabaseBuilder.java`, `DbModule.java`

---

### C2. `Database.close()` 只关空闲连接 → 完善关闭流程

**问题**：仅遍历 `idle` 队列，活跃连接泄漏，cleaner 线程残留。

**方案**：三步关闭 + 等待活跃连接归还。

**修改：ConnectionPool.close()**

```java
@Override
public void close() {
    closed = true;

    // 1. 阻止新连接创建 — 中断当前等待的 borrow
    //    (closed 已设, ensureOpen 会拦截新请求)

    // 2. 关闭所有空闲连接
    PooledConnection conn;
    while ((conn = idle.pollFirst()) != null) {
        closePhysical(conn);
        total.decrementAndGet();  // ← 新增：保持 total 准确
    }

    // 3. 等待活跃连接归还（有限超时，最多等 connectionTimeout）
    long deadline = System.nanoTime() + config.connectionTimeout().toNanos();
    while (total.get() > 0 && System.nanoTime() < deadline) {
        Thread.yield();  // 或 LockSupport.parkNanos(10_000_000)
    }

    // 4. 强制关闭剩余的活跃连接（标记为 invalid，归还时走 destroy）
    conn = idle.pollFirst();  // 可能有刚归还的
    while (conn != null) {
        closePhysical(conn);
        total.decrementAndGet();
        conn = idle.pollFirst();
    }

    // 5. 等待 cleaner 线程退出
    //    需要保存 cleaner 线程引用：
    //    cleanThread.join(timeout);
}
```

**修改：ConnectionPool** — 保存 cleaner 线程引用：

```java
private Thread cleanThread;

private void startCleaner() {
    cleanThread = Thread.ofVirtual()
        .name("freeway-db-cleaner")
        .start(() -> { /* 现有逻辑 */ });
}
```

**修改：release()** — 关闭后走销毁路径（已实现 `if (closed || !isAlive(conn)) destroy`）

**涉及文件**：`ConnectionPool.java`

---

### C3. `createConnection()` 无健康检查 → 新增轻量验证

**问题**：新创建的连接没有立即验证，死连接延后暴露。

**方案**：`createConnection()` 成功后执行一次快速验证。

**修改：ConnectionPool.createConnection()**

```java
private PooledConnection createConnection() {
    try {
        Connection jdbcConn;
        if (config.properties().containsKey("user")) {
            jdbcConn = DriverManager.getConnection(config.url(), config.properties());
        } else {
            jdbcConn = DriverManager.getConnection(config.url());
        }
        jdbcConn.setAutoCommit(true);

        // ← 新增：轻量验证
        if (!jdbcConn.isValid((int) config.healthCheckTimeout().toSeconds())) {
            jdbcConn.close();
            throw new SqlException("Newly created connection failed health check");
        }

        return new PooledConnection(jdbcConn, Instant.now());
    } catch (SQLException e) {
        throw new SqlException("Failed to create connection: " + e.getMessage(), e);
    }
}
```

**可选优化**：通过配置控制是否开启创建时验证（`pool.validate-on-create=true`），防止所有场景都多一次 RTT。

**涉及文件**：`ConnectionPool.java`, `PoolConfig.java`, `DatabaseBuilder.java`

---

## 🟠 High 修复

### H1. 每次借用做健康检查 → 改为基于空闲时间验证

**问题**：`borrow()` 对每从 idle 取出的连接都执行 `healthCheck()`（SELECT 1）。

**方案**：仅在连接空闲超过阈值时才执行健康检查。

**修改：ConnectionPool.borrow()**

```java
private static final Duration FRESH_IDLE_THRESHOLD = Duration.ofSeconds(5);

public PooledConnection borrow() {
    // ... acquire semaphore ...

    PooledConnection conn = idle.pollFirst();
    if (conn != null) {
        // 快速路径：刚归还的连接跳过健康检查
        if (conn.isFresh(FRESH_IDLE_THRESHOLD) || isValid(conn)) {
            success = true;
            return conn;
        }
        destroy(conn);
    }
    // ... 创建新连接 ...
}
```

**PooledConnection** — 新增 `isFresh()` 方法：

```java
boolean isFresh(Duration threshold) {
    return Duration.between(lastReturned, Instant.now()).compareTo(threshold) < 0;
}
```

或通过配置项控制：

```java
config.add("freeway.db.pool.validation-interval", "5 s");
```

**涉及文件**：`ConnectionPool.java`, `PooledConnection.java`

---

### H2. `stream()` 连接生命周期缺陷 → 改进资源管理

**问题**：Stream 返回后连接被持有直到显式 close()，用户容易泄漏。

**方案 A — 文档强调 + 资源跟踪**：

在 `Query.stream()` 的 javadoc 中增加显式警告，示例编码规范。

**方案 B — 自动释放包装（推荐）**：

```java
public <T> Stream<T> stream(Class<T> targetType) {
    try {
        var ctx = borrow();
        bindAll(ctx.stmt);
        var rs = ctx.stmt.executeQuery();
        var mapper = db.rowMapper.forClass(targetType);

        var iterator = new Iterator<T>() {
            private boolean hasNext = rs.next();
            private int rowNum = 0;
            private volatile boolean closed = false;

            private void ensureOpen() {
                if (closed) throw new IllegalStateException("Stream already closed");
            }

            @Override
            public boolean hasNext() {
                ensureOpen();
                if (!hasNext) {
                    safeClose();
                    return false;
                }
                return true;
            }

            @Override
            public T next() {
                ensureOpen();
                try {
                    T result = mapper.map(rs, rowNum++);
                    hasNext = rs.next();
                    if (!hasNext) safeClose();
                    return result;
                } catch (SQLException e) {
                    safeClose();
                    throw new SqlException("Stream read failed", e);
                }
            }

            private void safeClose() {
                if (closed) return;
                closed = true;
                closeAll(rs, ctx.stmt, ctx.connectionSource());
            }
        };

        var spliterator = Spliterators.spliteratorUnknownSize(iterator, 0);
        return StreamSupport.stream(spliterator, false)
            .onClose(() -> { if (iterator instanceof AutoCloseable) ((AutoCloseable)iterator).safeClose(); })
            .sequential();
    } catch (SQLException e) {
        throw new SqlException("Stream query failed", e.getMessage(), e);
    }
}
```

**方案 C — 提供 `forEach`/`collect` 终结方法**（增加 API 但不破坏兼容）：

```java
// 在 Query 接口中新增:
default <T> void forEach(Class<T> targetType, Consumer<T> action) {
    try (var stream = stream(targetType)) {
        stream.forEach(action);
    }
}

default <T, R> R collect(Class<T> targetType, Collector<T, ?, R> collector) {
    try (var stream = stream(targetType)) {
        return stream.collect(collector);
    }
}
```

**涉及文件**：`Query.java`, `QueryImpl.java`

---

### H3. `DatabaseBuilder` 边界校验 → 完整参数校验

**问题**：缺少对 maxSize 上限、URL 格式、超时值等的校验。

**修改：DatabaseBuilder.build()**

```java
public Database build() {
    if (url == null || url.isBlank())
        throw new IllegalStateException("url is required");
    if (!url.startsWith("jdbc:"))
        throw new IllegalStateException("url must start with 'jdbc:'");
    if (username == null)
        throw new IllegalStateException("username is required");
    if (password == null)
        throw new IllegalStateException("password is required");
    if (maxSize < 1 || maxSize > 1024)
        throw new IllegalStateException("maxSize must be between 1 and 1024");
    if (minIdle < 0 || minIdle > maxSize)
        throw new IllegalStateException("minIdle must be between 0 and maxSize");
    if (connectionTimeout == null || connectionTimeout.toMillis() <= 0)
        throw new IllegalStateException("connectionTimeout must be positive");
    if (maxLifetime == null || maxLifetime.toMillis() <= 0)
        throw new IllegalStateException("maxLifetime must be positive");
    if (maxIdleTime == null || maxIdleTime.toMillis() <= 0)
        throw new IllegalStateException("maxIdleTime must be positive");
    if (cleanInterval == null || cleanInterval.toMillis() <= 0)
        throw new IllegalStateException("cleanInterval must be positive");
    if (healthCheckQuery == null || healthCheckQuery.isBlank())
        throw new IllegalStateException("healthCheckQuery is required");
    if (healthCheckTimeout == null || healthCheckTimeout.toMillis() <= 0)
        throw new IllegalStateException("healthCheckTimeout must be positive");
    // ...
}
```

**涉及文件**：`DatabaseBuilder.java`

---

## 🟡 Medium 修复

### M1. `clean()` 的 permit/total 计数漂移

**问题**：`clean()` 销毁 idle 连接时 `total.decrementAndGet()` 但不影响 semaphore，产生临时"幽灵" permit。

**方案 A — 推荐：统一计数管理**

将控制模型改为：**permit = 物理连接数**。`total = 当前持有 permit 数 + idle 中 permit 数`。

不再独立管理 `total`，直接用 `semaphore.availablePermits()` 推算：

```java
public DatabaseStats stats() {
    int total = config.maxSize() - semaphore.availablePermits() + idle.size();
    return new DatabaseStats(
        total - idle.size(),  // active
        idle.size(),
        total,
        semaphore.getQueueLength(),
        config.maxSize());
}
```

这样统计口径基于 semaphore 状态，不依赖 `total` 原子变量，不会漂移。

**方案 B — `destroy()` 释放 permit**：

修改 `destroy()`，让销毁连接时释放一个 permit（调用者自行调整）：

```java
private void destroy(PooledConnection conn) {
    closePhysical(conn);
    total.decrementAndGet();
    // 注意：调用者如果从 borrow() 调用（持有 permit），不能重复 release
    // 只有 clean()（不持 permit）的调用才需要 release
}
```

但这需要区分调用者，增加复杂度。推荐方案 A。

**涉及文件**：`ConnectionPool.java`

---

### M2. `findColumn()` O(n²) 列扫描 → 构建一次列映射

**问题**：每行每列都扫描 `ResultSetMetaData`，大数据量下性能差。

**方案**：在创建 `RowMapper` 时提前构建列名→索引的映射。

**修改：DefaultRowMapper** — Record 和 Bean mapper 共享列映射构建：

```java
// 新增：列名→索引映射构建
private static int[] buildColumnMapping(ResultSetMetaData meta, String[] propertyNames)
    throws SQLException {
    int[] mapping = new int[propertyNames.length];
    Arrays.fill(mapping, -1);

    // 先构建列名→索引索引
    Map<String, Integer> colIndex = new HashMap<>();
    for (int i = 1; i <= meta.getColumnCount(); i++) {
        String label = meta.getColumnLabel(i);
        if (label == null) label = meta.getColumnName(i);
        colIndex.put(label.toLowerCase(), i);
    }

    for (int i = 0; i < propertyNames.length; i++) {
        String prop = propertyNames[i];
        // 直接匹配
        Integer idx = colIndex.get(prop.toLowerCase());
        if (idx != null) { mapping[i] = idx; continue; }
        // snake_case 转换后匹配
        idx = colIndex.get(camelToSnake(prop).toLowerCase());
        if (idx != null) { mapping[i] = idx; continue; }
    }
    return mapping;
}
```

Record mapper 改为：

```java
private <T> RowMapper<T> createRecordMapper(Class<T> type) {
    var components = type.getRecordComponents();
    var ctor = canonicalConstructor(type, components);
    String[] names = Arrays.stream(components)
        .map(RecordComponent::getName).toArray(String[]::new);

    return (rs, rowNum) -> {
        if (mapping == null) {  // 懒构建，仅一次
            mapping = buildColumnMapping(rs.getMetaData(), names);
        }
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            int col = mapping[i];
            args[i] = col >= 0
                ? coerce(rs.getObject(col), components[i].getType())
                : nullValue(components[i].getType());
        }
        return ctor.newInstance(args);
    };
}
```

**注意**：`ResultSetMetaData` 在 `rs.getMetaData()` 上获取，必须在迭代 ResultSet 之前（mapper 回调内部第一次调用时）。首次调用时构建，之后复用。

**涉及文件**：`DefaultRowMapper.java`

---

### M3. TOCTOU 竞态 → `PooledConnection` 增加关闭标记

**问题**：`borrow()` 和 `clean()` 之间对同一连接的 TOCTOU。

**方案**：`PooledConnection` 增加原子关闭标记，防止双重关闭。

```java
public class PooledConnection {
    private final Connection jdbcConnection;
    private final Instant createdAt;
    private volatile Instant lastReturned;
    private final AtomicBoolean closed = new AtomicBoolean(false);  // ← 新增

    // 仅调用一次 closePhysical，多次调用安全
    boolean tryClose() {
        if (closed.compareAndSet(false, true)) {
            try {
                jdbcConnection.close();
                return true;
            } catch (SQLException ignored) {}
        }
        return false;
    }

    boolean isClosed() {
        return closed.get();
    }
}
```

`ConnectionPool` 中改为：

```java
private void closePhysical(PooledConnection conn) {
    if (conn.tryClose()) {  // ← 仅实际关闭时
        total.decrementAndGet();
    }
}
```

`borrow()` 在 `isValid()` 通过后、返回前增加二次确认：

```java
if (isValid(conn)) {
    if (!conn.isClosed()) {  // ← 二次确认
        success = true;
        return conn;
    }
    destroy(conn);  // 已被 clean 关闭
}
// 继续创建新连接...
```

**涉及文件**：`PooledConnection.java`, `ConnectionPool.java`

---

### M4. 健康检查异常静默 → 增加 WARN 日志

**问题**：`healthCheck()` 在 `healthCheck()` 中 `catch (SQLException e)` 直接返回 false，没有日志。

**修改：ConnectionPool.healthCheck()**

```java
private boolean healthCheck(PooledConnection conn) {
    try {
        // ... 现有逻辑 ...
    } catch (SQLException e) {
        // ← 新增日志
        if (logger.isWarnEnabled()) {
            logger.warn("Connection health check failed: {}", e.getMessage());
        }
        return false;
    }
}
```

**涉及文件**：`ConnectionPool.java`（需注入 Logger）

---

## 🔵 Low 修复

### L1. `camelToSnake()` 缩写处理

**改进算法**：

```java
static String camelToSnake(String camel) {
    var sb = new StringBuilder();
    for (int i = 0; i < camel.length(); i++) {
        char c = camel.charAt(i);
        if (Character.isUpperCase(c)) {
            if (i > 0) {
                char prev = camel.charAt(i - 1);
                char next = i + 1 < camel.length() ? camel.charAt(i + 1) : '\0';
                // 前一个字符是小写 → 新词开始
                if (Character.isLowerCase(prev)) {
                    sb.append('_');
                }
                // 后一个字符是小写（且前一个不是小写）→ 缩写结束
                else if (next != '\0' && Character.isLowerCase(next) && !Character.isLowerCase(prev)) {
                    sb.append('_');
                }
            }
            sb.append(Character.toLowerCase(c));
        } else {
            sb.append(c);
        }
    }
    return sb.toString();
}
```

测试结果：
| 输入 | 原结果 | 改进后 | 期望 |
|------|--------|--------|------|
| `userURL` | `user_u_r_l` | `user_url` | ✅ |
| `HTMLParser` | `h_t_m_l_parser` | `html_parser` | ✅ |
| `createdAt` | `created_at` | `created_at` | ✅ |
| `userId` | `user_id` | `user_id` | ✅ |

**涉及文件**：`DefaultRowMapper.java`

---

### L2. `camelToSnake()` 结果缓存

**方案**：在 `DefaultRowMapper` 中增加 `ConcurrentHashMap` 缓存。

```java
private static final Map<String, String> SNAKE_CACHE = new ConcurrentHashMap<>();

static String camelToSnake(String camel) {
    return SNAKE_CACHE.computeIfAbsent(camel, k -> {
        var sb = new StringBuilder();
        // ... 转换逻辑 ...
        return sb.toString();
    });
}
```

属性名通常是固定的类字段，数量有限，缓存几乎 100% 命中。

**涉及文件**：`DefaultRowMapper.java`

---

### L3. 静默吞异常 → 改为 warn 日志

**修改点清单**：

| 位置 | 当前 | 改为 |
|------|------|------|
| `DatabaseImpl.rollbackSilent()` | `catch (SQLException ignored) {}` | `logger.warn("Rollback failed", e)` |
| `ConnectionPool.closePhysical()` | `catch (SQLException ignored) {}` | `logger.debug("Connection close ignored", e)` |
| `QueryImpl.closeAll()` | `catch (SQLException ignored) {}` | `logger.trace("Resource close", e)` |
| `DatabaseImpl.closeConnection()` | `catch (SQLException ignored) {}` | `logger.trace("AutoCommit restore", e)` |

**原则**：
- rollback 失败 → `warn`（业务语义上重要）
- close 失败 → `debug`/`trace`（JVM 快关闭时正常）
- 恢复 autoCommit 失败 → `trace`

**涉及文件**：`DatabaseImpl.java`, `ConnectionPool.java`, `QueryImpl.java`

---

## 修复优先级建议

| 优先级 | 问题 | 工作量 | 收益 |
|--------|------|--------|------|
| P0 | C1 无查询超时 | ~10 行 | 防止线程永久阻塞 |
| P0 | C2 close() 泄漏 | ~30 行 | 资源正确释放 |
| P1 | H1 健康检查频率 | ~5 行 | 高并发下延迟降低 |
| P1 | H3 边界校验 | ~15 行 | 防御性编程 |
| P1 | L1 camelToSnake 缩写 | ~20 行 | 映射正确性 |
| P2 | C3 创建时验证 | ~5 行 | 快速失败 |
| P2 | M2 列映射性能 | ~40 行 | 大结果集性能 |
| P2 | L3 异常日志 | ~10 行 | 可观测性 |
| P3 | H2 stream 改进 | ~30 行 | API 安全性 |
| P3 | M1 计数漂移 | ~5 行 | 统计数据准确 |
| P3 | M3 TOCTOU | ~30 行 | 并发安全 |
| P4 | L2 缓存 | ~5 行 | 微优化 |
