# freeway-db 实现评估：问题与不足

> 基于 freeway-db 1.0.0 源码分析。原则：零外部依赖是好设计，但不应以正确性、性能、可观测性为代价。

---

## 概览

| 等级 | 数量 | 核心领域 |
|------|------|----------|
| 🔴 Critical | 3 | 资源泄漏、查询失控、延迟感知缺失 |
| 🟠 High | 3 | 性能、可靠性、初始化缺陷 |
| 🟡 Medium | 4 | 异常处理、清理由、并发边界 |
| 🔵 Low | 3 | 代码质量、边界校验 |

---

## 🔴 Critical

### C1. 无查询超时 — SQL 失控时线程永久阻塞

**问题位置**：`QueryImpl`（`freeway-db/src/main/java/com/jujin/freeway/db/internal/QueryImpl.java`）和 `BatchQueryImpl` 的所有执行路径。

**根因**：`PreparedStatement` 在执行查询前未调用 `setQueryTimeout()`。

```java
// QueryImpl.borrow() — 第 160-175 行
var stmt = conn.jdbcConnection()
    .prepareStatement(jdbcSql(), Statement.NO_GENERATED_KEYS);
return new ExecuteContext(stmt, conn, db.pool);
// 没有任何 stmt.setQueryTimeout(...)
```

**影响**：
- 一个慢 SQL（锁竞争、全表扫描、DB 抖动）永久阻塞线程
- Virtual Thread 不会阻塞 OS 线程，但连接被挂起，不归还池
- 积累到 `maxSize` 后，整个应用的所有 DB 操作全部夯住

**建议修复**：在 `borrow()` 创建 `PreparedStatement` 后设置超时，通过 `DbModule` 配置 `@Symbol("freeway.db.query-timeout")`，默认 30s。

---

### C2. `Database.close()` 只关闭空闲连接 — 活跃连接泄漏

**问题位置**：`ConnectionPool.close()`（`freeway-db/src/main/java/com/jujin/freeway/db/internal/ConnectionPool.java:150`）

```java
public void close() {
    closed = true;
    PooledConnection conn;
    while ((conn = idle.pollFirst()) != null) {
        closePhysical(conn);     // 只清理了 idle 队列
    }
    // 活跃连接还在外面，total 没有归零
    // cleaner 线程也没有 join
}
```

**根因**：
- 仅遍历 `idle` 队列，当前还在 `borrow()` 手中的活跃连接完全不管
- `total` 计数器不归零，`stats()` 返回的数据不反映真实状态
- cleaner 虚拟线程没有 `join`，可能残留

**影响**：
- 应用停止后，活跃连接变成泄漏的 TCP 连接（直到 DB 端 timeout）
- 在 `@Startup` 中初始化 DB 然后断开再重连的场景下会泄漏
- 如果 JVM 关闭时 cleaner 线程还在运行，可能写入已关闭的连接

**建议修复**：
```java
public void close() {
    closed = true;
    // 1. 阻止新请求
    // 2. 关闭所有空闲连接
    PooledConnection conn;
    while ((conn = idle.pollFirst()) != null) {
        closePhysical(conn);
        total.decrementAndGet();
    }
    // 3. 无法强制归还活跃连接，但至少设置标志让 release() 走销毁路径
    // 4. 可选：等待所有活跃连接归还（有限超时）
    // 5. 关闭 cleaner 线程
}
```

---

### C3. `createConnection()` 无健康检查 — 死连接延迟感知

**问题位置**：`ConnectionPool.createConnection()`（第 177-191 行）

```java
private PooledConnection createConnection() {
    try {
        Connection jdbcConn = DriverManager.getConnection(...);
        jdbcConn.setAutoCommit(true);
        return new PooledConnection(jdbcConn, Instant.now());
        // 无健康检查！
    } catch (SQLException e) {
        throw new SqlException("Failed to create connection: " + e.getMessage(), e);
    }
}
```

**对比**：
- `borrow()` 从 `idle` 取连接时调用 `isValid()` → 做健康检查 ✅
- `borrow()` 创建新连接时 **不做** 任何健康检查 ❌

**影响**：如果数据库出现瞬时故障，`DriverManager.getConnection()` 可能返回一个看似正常但实际不可用的连接。用户 SQL 执行失败时才暴露问题，增加了故障排查难度。

**建议修复**：`createConnection()` 成功后加入可选的轻量验证（如 `connection.isValid(timeout)`）。

---

## 🟠 High

### H1. 每次从空闲池借连接都做健康检查 — 不必要的延迟

**问题位置**：`ConnectionPool.borrow()` → `isValid(conn)`（第 198-199 行）

```java
if (isValid(conn)) {
    success = true;
    return conn;
}
// isValid() 内部执行：
// 1. isAlive() — 检查过期时间
// 2. healthCheck() — 执行 SELECT 1
```

**根因**：每次从 idle 取连接都运行 `healthCheck()`，包括刚归还不到 1 秒的热连接。

**影响**：
- 高并发场景：每次 `borrow()` 额外一次 JDBC round-trip
- `SELECT 1` 本身很快，但网络 RTT 在跨机房部署下可能是 1-5ms
- 1000 QPS 的应用上，每秒额外 1000 次健康查询

**建议**：将验证策略改为：
- 连接空闲超过 `N` 秒（如 `validation-interval=5s`）才执行健康检查
- 或依赖 `isValid(timeout)` 的 JDBC 驱动层检查（部分驱动无额外网络开销）
- 保留对到期连接的强制健康检查

---

### H2. `stream()` 的连接生命周期依赖用户正确关闭 Stream

**问题位置**：`QueryImpl.stream()`（第 109-142 行）

```java
return StreamSupport.stream(spliterator, false).onClose(() ->
    closeAll(rs, ctx.stmt, ctx.connectionSource()));
```

**问题**：
- Stream 返回后，连接被持有到 `Stream.close()` 被调用
- 如果调用者中途短路（`break`/`return`/`异常`）而不 `close()`，连接泄漏
- `onClose` 只在显式 `close()` 或 `try-with-resources` 上触发
- Stream API 的传统陷阱：开发者假定 `forEach`/`collect` 会自动关闭（不会）

**建议**：
- 添加 javadoc 强烈强调必须用 `try(...)` 包围 stream
- 或在 iterator 的 `hasNext()` 返回 false 时也保证资源释放（当前已经做了，但异常路径可能不完整）
- 考虑增加 `forEachRemaining` 风格的终结方法

---

### H3. `DatabaseBuilder` 缺少边界校验

**问题位置**：`freeway-db/src/main/java/com/jujin/freeway/db/DatabaseBuilder.java`

```java
if (maxSize < 1) throw new IllegalStateException("maxSize must be >= 1");
```

**缺失校验**：
| 字段 | 应校验 | 风险 |
|------|--------|------|
| `maxSize` | 无上限 | `maxSize = Integer.MAX_VALUE` → 耗尽系统 FD |
| `url` | jdbc 协议格式 | 拼写错误到运行时才暴露 |
| `connectionTimeout` | 非 null, >0 | null → NPE |
| `maxLifetime/maxIdleTime` | > minIdle check | 可能导致频繁创建/销毁 |
| `healthCheckQuery` | 非空 | 空字符串 → 无意义查询 |

---

## 🟡 Medium

### M1. `clean()` 清理空闲连接后 permit/total 计数漂移

**问题位置**：`ConnectionPool.clean()`（第 226-257 行）

**场景**：
```
maxSize=10, idle中有2个过期连接, total=10
clean() 清理 2 个过期连接：total=8, idle=0
但 semaphore 中有 2 个"幽灵"permit（来自之前 release() 的连接）
→ availablePermits = 2 (maxSize - total)
→ 新的 borrow: acquire permit → 创建新连接 → total=9
→ 再 borrow: acquire 幽灵 permit → 创建新连接 → total=10
→ 最终能稳定回来 ✓
```

**但是**在极端并发下会出现 transient 不一致：
```
borrow() 在 clean() 执行过程中并发:
1. clean() 销毁过期连接: total=8, 释放了物理 JDBC 连接
2. borrow() 在 clean() 中间 acquire 了幽灵 permit
3. total.get()=8 < maxSize → 创建新连接
4. createConnection 成功 → 但实际上 DB 已有 10 个连接
```

**影响**：虽然最终能收敛，但短时间内可能超过 `maxSize` 实际物理连接数。对严格控制连接数的环境（如 PgBouncer 的 `max_client_conn`）可能触发连接拒绝。

**建议**：semaphore permit 和 total 计数应该通过同一个 CAS 操作保持一致。或改为在 `release()` 时记录 permit token，clean 时归还。

---

### M2. `findColumn()` O(n*m) 全表扫描每次映射

**问题位置**：`DefaultRowMapper.findColumn()`（第 231-248 行）

```java
static int findColumn(ResultSetMetaData meta, String propertyName) {
    for (int i = 1; i <= meta.getColumnCount(); i++) {       // 第一遍
        String col = meta.getColumnLabel(i);
        if (propertyName.equalsIgnoreCase(col)) return i;
    }
    String snake = camelToSnake(propertyName);
    for (int i = 1; i <= meta.getColumnCount(); i++) {       // 第二遍
        String col = meta.getColumnLabel(i);
        if (snake.equalsIgnoreCase(col)) return i;
    }
    return -1;
}
```

**问题**：对 **每一行每一列** 都扫描 ResultSetMetaData（遍历列名），复杂度 `O(rows × cols²)`。

```java
// 对于一个 1000 行 20 列的查询：
// 每次映射 = 20 次 findColumn 调用 × 每次 O(20) = 400 比较
// 1000 行 = 400,000 次字符串比较！
```

**Bean mapper 更严重**：`createBeanMapper()` 在 `for (String propName : adapter.getPropertyNames())` 中对每个属性都调用 `findColumn()`。Bean 通常比 Record 有更多属性。

**建议**：在 `forClass()` 创建 mapper 时，提前构建 `{列名 → 位置}` 的 `int[]` 映射数组。只需要执行一次 `ResultSetMetaData` 扫描，而不是每行扫描。

---

### M3. `isValid()` 的 TOCTOU 竞态

**问题位置**：`ConnectionPool.borrow()` + `clean()`

```
时间线:
T1: borrow() → idle.poll() → conn
T2: clean()  → iterator 看到同一 conn (还未被 poll 移除)
T3: borrow() → isValid(conn) → true → return conn
T4: clean()  → isExpired(conn) → true → it.remove() + closePhysical(conn)
                  ← 连接在 T3 被返回给用户，然后在 T4 被关闭！
```

**根因**：`ConcurrentLinkedDeque` 的 iterator 是 weakly consistent，`clean()` 可能看到一份 poll 之前的 snapshot。

**影响**：用户持有的连接在 `T4` 被 `closePhysical()` 关闭，后续 SQL 执行报 `Connection closed` 错误。

**修复**：`clean()` 在 `closePhysical()` 前加 `closed` 原子标记，或 `PooledConnection` 增加 `closed` 标志。`borrow()` 返回前二次确认。

---

### M4. `healthCheck()` 在失效连接上可能被其他线程误用

**问题位置**：`ConnectionPool.healthCheck()`（第 207-219 行）

```java
try (Statement stmt = jdbcConn.createStatement()) {
    stmt.setQueryTimeout(...);
    stmt.execute(config.healthCheckQuery());
    return true;
}
```

`jdbcConn` 在健康检查期间，另一个线程可能同时拿来执行 SQL。虽然 `isValid()` 在 borrow 路径中持有锁（借出中），但如果连接正在被 health check 的同时被另一个 borrow 路径获取到... 实际上 borrow 的 `idle.poll()` 是原子移除的，所以不会发生。这个风险较低，但值得注意。

---

## 🔵 Low

### L1. `camelToSnake()` 对缩写映射不符合惯例

```java
static String camelToSnake(String camel) {
    for (int i = 0; i < camel.length(); i++) {
        char c = camel.charAt(i);
        if (Character.isUpperCase(c)) {
            if (i > 0) sb.append('_');
            sb.append(Character.toLowerCase(c));
        } else {
            sb.append(c);
        }
    }
    return sb.toString();
}
```

**问题**：
- `userURL` → `user_u_r_l`（应为 `user_url` 或全大写 `user_url`）
- `HTMLParser` → `h_t_m_l_parser`（应为 `html_parser`）

**影响**：列名包含缩写的场景下映射失败。

**建议**：常见的 `camelToSnake` 算法会识别连续大写字母序列，将最后一个大写字母之前的部分视为一个缩写词（如 `userURL` → `user_url`，`XMLParser` → `xml_parser`）。

---

### L2. `camelToSnake()` 每次调用重新构建 StringBuilder

对千行结果集的每次列匹配都调用 `camelToSnake()`，每次都 new StringBuilder。可以缓存属性名→snake 结果。

---

### L3. 异常处理中过度沉默

```java
// DatabaseImpl.java 第 95 行
void rollbackSilent() {
    try {
        conn.jdbcConnection().rollback();
        conn.jdbcConnection().setAutoCommit(true);
    } catch (SQLException ignored) {
    }                                     // ← 静默吞掉！
}
```

虽然 rollback 失败通常不可恢复，但至少应该 warn 日志。同样问题出现在 `closePhysical()`、`closeAll()`、`closeConnection()` 等多个 catch 块。

---

## 总结

| 类别 | 数量 | 最严重问题 |
|------|------|-----------|
| 资源泄漏风险 | 2 | C2 (close 不关活跃连接), H2 (stream 泄漏) |
| 线程阻塞风险 | 1 | C1 (无查询超时) |
| 性能问题 | 3 | H1 (每次验证), M2 (O(n²)列匹配), L2 (无缓存) |
| 并发正确性 | 2 | M1 (permit计数漂移), M3 (TOCTOU) |
| 可观测性 | 2 | C3 (创建无验证), L3 (吞异常) |
| 防御性编程 | 2 | H3 (边界校验), M4 (健康检查) |

整体评价：在 **零外部依赖** 的前提下，连接池和查询执行的核心逻辑是基本正确的（Semaphore + ConcurrentLinkedDeque 的设计经过了仔细的考虑）。主要的工程缺陷集中在 **防御性不足**（无超时、无边界、吞异常）和 **性能优化遗漏**（每行全表扫描列名、每次借用做健康检查），这些是生产环境中最容易出问题的方向。
