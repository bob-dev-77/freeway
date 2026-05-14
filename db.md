# Freeway DB 模块深度剖析

> 基于 freeway-db 源码分析，JDK 25+，从第一性原理出发的零外部依赖数据库访问层。

---

## 1. 设计哲学

从 Go `database/sql` 借鉴核心思想，在 Java 25 上用极简原则重建数据库访问层：

| 原则 | 体现 |
|------|------|
| **一个概念一个对象** | `Database` = 连接池 + 查询入口，不需要 DataSource + HikariCP + JdbcTemplate 三层分离 |
| **显式优于隐式** | SQL 是第一公民，不做 JPQL/HQL 抽象，不做懒加载和脏检查 |
| **约定优于配置** | 列名自动 snake_case → camelCase 转换，Record/Bean 自动映射 |
| **利用平台能力** | Virtual Threads、ScopedValue（预留）、MethodHandle |

---

## 2. 模块全景

```
freeway-db/
├── Database.java          核心接口 — 数据库句柄即连接池
├── DatabaseBuilder.java   构建器
├── Query.java             查询构建器（命名/位置参数，list/one/execute/stream）
├── BatchQuery.java        批量操作
├── Transaction.java       事务（lambda 式 + try-with-resources 手动式）
├── RowMapper.java         自定义行映射函数式接口
├── RowMapperOverrides.java 自定义映射贡献点
├── DatabaseStats.java     池统计 record
├── SqlException.java      非受检 SQL 异常
├── DbModule.java          IoC Module（服务注册、连接池默认配置）
├── DbModuleProvider.java  SPI 自动发现
├── DbHub.java             多数据源注册中心
├── annotations/
│   ├── Primary.java       主库 marker
│   └── ReadOnly.java      只读库 marker
└── internal/
    ├── ConnectionPool.java      Semaphore + ConcurrentLinkedDeque 连接池
    ├── PoolConfig.java          池配置 record
    ├── PooledConnection.java    JDBC Connection 包装
    ├── DatabaseImpl.java        Database 实现 + 事务实现
    ├── QueryImpl.java           Query 实现
    ├── BatchQueryImpl.java      BatchQuery 实现
    ├── DefaultRowMapper.java    Record/Bean/简单类型 自动映射（319行）
    ├── NamedParamParser.java    :name / #name → ? 占位符解析
    ├── MigrationRunner.java     轻量 migration（ClassPathScanner 扫描）
    ├── DbHubImpl.java           DbHub 实现
    └── RowMapperOverridesImpl.java
```

---

## 3. 核心接口

### 3.1 Database — 万能的数据库句柄

`Database` 既是连接池也是查询入口。位于 `freeway-db/src/main/java/com/jujin/freeway/db/Database.java`。

```java
public interface Database extends AutoCloseable {
    Query sql(String sql, Object... params);           // 创建参数化查询
    BatchQuery batch(String sql);                      // 批量操作
    void transaction(Consumer<Transaction> work);      // Lambda 事务
    Transaction beginTransaction();                    // 手动事务
    boolean ping();                                    // 健康检查
    DatabaseStats stats();                             // 池统计
    void close();                                      // 关闭连接池
}
```

**两种参数风格**：

```java
// 位置参数 ?
db.sql("SELECT * FROM t WHERE a = ? AND b = ?", 1, 2);

// 命名参数 #name（推荐，不会与 SQL :: 冲突）
db.sql("INSERT INTO t (id, name) VALUES (#id, #name)")
   .param("name", "bob").param("id", 100001);

// 命名参数 :name（也支持）
db.sql("SELECT * FROM t WHERE name = :name").param("name", "Bob");
```

### 3.2 Query — 流式查询接口

```java
public interface Query {
    Query param(String name, Object value);         // 绑定命名参数
    <T> List<T>   list(Class<T> targetType);        // 结果列表
    <T> Optional<T> one(Class<T> targetType);       // 单行结果
    int execute();                                   // DML 执行
    <T> Stream<T> stream(Class<T> targetType);      // 懒加载流
}
```

`Query` 是单次使用的：执行一次后即被消费。

### 3.3 Transaction — 事务作用域

```java
// Lambda 式（推荐）— 自动 commit/rollback
db.transaction(tx -> {
    tx.sql("UPDATE a SET bal = bal - ? WHERE id = ?", 100, 1).execute();
    tx.sql("UPDATE b SET bal = bal + ? WHERE id = ?", 100, 2).execute();
});

// 手动式 — try-with-resources
try (var tx = db.beginTransaction()) {
    tx.sql("...").execute();
    tx.commit();
} // auto-rollback if not committed
```

两种方式的事务实现都在 `DatabaseImpl.TransactionImpl` 中（`freeway-db/src/main/java/com/jujin/freeway/db/internal/DatabaseImpl.java:72`），共享同一个连接。

---

## 4. 连接池实现 — Semaphore + ConcurrentLinkedDeque

### 4.1 架构

`ConnectionPool`（`freeway-db/src/main/java/com/jujin/freeway/db/internal/ConnectionPool.java`，257行）：

```
                    Semaphore(maxSize)
                         │
              ┌──────────┼──────────┐
              │                     │
         tryAcquire(timeout)    release()
              │                     ↑
              ↓                     │
    ┌─── idle.pollFirst() ──┐   idle.offer(conn)
    │                       │
    │ 是：isValid(conn)?    │
    │                       │
    ├─ 是 → 返回连接        │
    └─ 否 → destroy(conn)   │
         → 重试             │
                            │
    idle 为空：createConnection()
         → total++
         → 返回新连接
```

### 4.2 borrow() 流程

```java
public PooledConnection borrow() {
    ensureOpen();
    // 1. 获取许可（阻塞等待）
    if (!semaphore.tryAcquire(config.connectionTimeout().toMillis(), MILLISECONDS))
        throw new SqlException("Connection pool exhausted...");
    
    // 2. 从空闲队列取连接
    PooledConnection conn = idle.pollFirst();
    if (conn != null) {
        if (isValid(conn)) return conn;
        destroy(conn);  // 无效连接，销毁并重试
    }
    
    // 3. 创建新连接或等待空闲连接
    try {
        while (true) {
            conn = idle.pollFirst();
            if (conn != null) {
                if (isValid(conn)) return conn;
                destroy(conn);
                continue;
            }
            if (total.get() < config.maxSize())
                return createConnection();
            // 等待归还
            // ...
        }
    } catch (...) { ... }
}
```

### 4.3 release() 流程

```java
public void release(PooledConnection conn) {
    conn.markReturned();           // 记录归还时间（用于空闲超时判断）
    if (isValid(conn))
        idle.offerFirst(conn);     // 有效 → 入队
    else
        destroy(conn);             // 无效 → 销毁
    semaphore.release();           // 释放许可
}
```

### 4.4 后台清理线程

一个虚拟线程定期执行 `clean()`：

1. **过期连接驱逐** — 遍历 `idle` 队列，检查是否超过 `maxLifetime` 或 `maxIdleTime`
2. **维持最小空闲** — 如果 `idle.size() < minIdle`，创建新连接补充

### 4.5 配置项

PoolConfig 是 Java Record（`freeway-db/src/main/java/com/jujin/freeway/db/internal/PoolConfig.java`）：

| 符号 | 默认值 | 说明 |
|------|--------|------|
| `freeway.db.pool.max-size` | 10 | 最大连接数 |
| `freeway.db.pool.min-idle` | 2 | 最小空闲连接 |
| `freeway.db.pool.connection-timeout` | 30s | 等待连接超时 |
| `freeway.db.pool.max-lifetime` | 30m | 连接最大存活时间 |
| `freeway.db.pool.max-idle-time` | 10m | 空闲连接最大存活时间 |
| `freeway.db.pool.clean-interval` | 1m | 后台清理间隔 |
| `freeway.db.pool.health-check-query` | `SELECT 1` | 连接验证查询 |
| `freeway.db.pool.health-check-timeout` | 5s | 健康检查超时 |

### 4.6 PooledConnection

```java
public class PooledConnection {
    private final Connection jdbcConnection;
    private final Instant createdAt;
    private volatile Instant lastReturned;

    boolean isExpired(Instant now, Duration maxLifetime, Duration maxIdleTime) {
        if (Duration.between(createdAt, now).compareTo(maxLifetime) > 0) return true;
        if (Duration.between(lastReturned, now).compareTo(maxIdleTime) > 0) return true;
        return false;
    }
}
```

---

## 5. 查询执行流程

### 5.1 QueryImpl 内部

`QueryImpl`（`freeway-db/src/main/java/com/jujin/freeway/db/internal/QueryImpl.java`，213行）：

```
sql(sql, args...) / param(name, value)
    ↓  存储参数
list(targetType) / one(targetType) / execute() / stream(targetType)
    ↓
borrow()                    ← 事务连接 或 从池借用
    ├─ 事务连接 != null → 使用事务连接
    └─ 事务连接 == null → db.pool.borrow()
    ↓
prepareStatement(jdbcSql()) ← 命名参数已转换为 ?
    ↓
bindAll(stmt)               ← 绑定位置/命名参数
    ↓
executeQuery() / executeUpdate()
    ↓
DefaultRowMapper.forClass(targetType).map(rs, rowNum)
    ↓
closeAll(rs, stmt, conn)    ← 归还连接
```

关键点：
- **事务内**：查询复用事务连接，不归还到池
- **事务外**：每次查询 borrow → release，回到空闲队列
- **命名参数**：`#name` / `:name` 正则替换为 `?`

### 5.2 NamedParamParser

位于 `freeway-db/src/main/java/com/jujin/freeway/db/internal/NamedParamParser.java`：

```java
// 正则：[:#]([a-zA-Z_][a-zA-Z0-9_]*)
// 输入：  "INSERT INTO t (id, name) VALUES (#id, #name)"
// 输出：  names = ["id", "name"], jdbcSql = "INSERT INTO t (id, name) VALUES (?, ?)"
```

支持 `#name` 和 `:name` 两种风格，解析结果缓存在 `QueryImpl` 中以减少正则开销。

### 5.3 ExecuteContext

内部 `record ExecuteContext(PreparedStatement stmt, PooledConnection conn, ConnectionPool pool)` 实现了 `AutoCloseable`，确保 `stmt.close()` 和连接归还。

---

## 6. 结果映射 — DefaultRowMapper

### 6.1 架构

`DefaultRowMapper`（`freeway-db/src/main/java/com/jujin/freeway/db/internal/DefaultRowMapper.java`，319行）：

```
forClass(targetType)
    ↓
1. 检查 RowMapperOverrides（用户自定义映射，最高优先级）
    → 有 → 返回自定义 mapper
    → 无 → cache.computeIfAbsent(type, this::create)
        ↓
2. type 是 Record？
    → 是 → createRecordMapper(type)
        ↓
        - 获取 RecordComponent[]（列名 → snake_case 匹配）
        - 获取 canonical constructor
        - 对每个组件：rs.getObject(col) + type coercion
        - 调用构造函数
    → 否
3. type 是简单类型（String/Integer/Long/...）？
    → 是 → rs.getObject(1, type)
    → 否
4. JavaBean 映射
    → BeanInfo / Introspector 获取属性
    → PropertyAccess（IoC 模式下）或直接 setter 写入
    → 列名 → snake_case → camelCase 匹配
```

### 6.2 Record 映射

Record 映射使用 canonical constructor：

```java
// 示例：
record User(long id, String name, String emailAddr, LocalDateTime createdAt) {}

// SQL: SELECT id, name, email_addr, created_at FROM users
// DefaultRowMapper 自动转换：
//   email_addr → emailAddr
//   created_at → createdAt
//   类型自动映射（Timestamp → LocalDateTime 等）
```

### 6.3 类型转换

支持三层类型转换：

| 层级 | 说明 |
|------|------|
| **IoC TypeCoercer** | 当可用时，通过多步转换链转换 |
| **内置 basicCoerce** | JDBC 类型 → Java 类型（如 `Date → LocalDate`、`Number → Boolean`） |
| **null 处理** | 原始类型返回默认值（0/false），引用类型返回 null |

内置转换映射：

```
BigDecimal → Integer/Long/Double/Float/Short/Byte
Timestamp  → LocalDateTime/Instant
Date       → LocalDate
Time       → LocalTime
Number     → boolean (n != 0)
```

### 6.4 列名匹配

自动 `snake_case` → `camelCase` 转换：

```
sql column           record component
─────────────────    ─────────────────
user_id           →  userId
email_addr        →  emailAddr
created_at        →  createdAt
```

通过 `CaseInsensitiveMap` 实现，匹配逻辑：
1. 直接匹配（如 `id` → `id`）
2. `snake_case → camelCase` 转换后匹配
3. 都找不到 → 跳过该列

### 6.5 自定义 RowMapper

通过贡献点注入：

```java
@Contribute(RowMapperOverrides.class)
public static void myMappers(MappedConfiguration<Class<?>, RowMapper<?>> config) {
    config.add(MyJsonType.class, (rs, rowNum) ->
        MyJsonType.parse(rs.getString("payload")));
}
```

自定义映射优先于内置映射，且不被缓存（因为可能随 reload 变化）。

---

## 7. 事务实现

### 7.1 两种模式

**Lambda 模式**（`DatabaseImpl.transaction()`）：
```java
public void transaction(Consumer<Transaction> work) {
    TransactionImpl tx = beginTransaction();
    try {
        work.accept(tx);       // 用户逻辑
        tx.commit();           // 自动 commit
    } catch (Throwable e) {
        tx.rollbackSilent();   // 自动 rollback
        throw e;
    } finally {
        tx.closeConnection();  // 归还连接 + 恢复 autoCommit
    }
}
```

**手动模式**（`DatabaseImpl.beginTransaction()`）：
```java
public TransactionImpl beginTransaction() {
    var conn = pool.borrow();              // 借一个连接
    conn.jdbcConnection().setAutoCommit(false);
    return new TransactionImpl(this, conn);
}
```

### 7.2 TransactionImpl 状态机

```
                 borrow()
                    ↓
              ┌─────────────────┐
              │ autoCommit=false │
              └────┬────────┬───┘
                   │        │
              commit()   rollback()
                   │        │
                   ▼        ▼
              ┌─────────────────┐
              │ finished = true │
              │ autoCommit=true │
              └────────┬────────┘
                       │
              pool.release(conn)
```

核心设计：
- `finished` 标志保证幂等（重复 commit/rollback 无副作用）
- `close()` 未提交时自动 rollback（try-with-resources 安全）
- `closeConnection()` 总是恢复 `autoCommit = true` 再归还

---

## 8. Migration — 轻量数据库迁移

### 8.1 文件约定

SQL 文件放在 `db/migration/` 类路径下：

```
src/main/resources/
  db/
    migration/
      V001__create_users.sql
      V002__add_email_column.sql
      V003__create_indexes.sql
```

命名规则：`V{序号}__{描述}.sql`。按文件名字典序执行。

### 8.2 执行流程

`MigrationRunner.run()`（`freeway-db/src/main/java/com/jujin/freeway/db/internal/MigrationRunner.java`）：

```
1. ensureMigrationTable()
   → CREATE TABLE IF NOT EXISTS _migrations (
       version     VARCHAR(255) PRIMARY KEY,
       description VARCHAR(512),
       executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)

2. scanMigrationFiles()
   → ClassPathScanner 扫描 db/migration/*.sql
   → 按文件名排序

3. completedVersions()
   → SELECT version FROM _migrations → Set<String>

4. 遍历文件：
   for each file {
       version = versionFromPath(path)     // 如 "V001"
       if (completed.contains(version)) continue  // 幂等跳过
       runMigration(version, sql)
           → db.transaction(tx -> {
               tx.sql(sql).execute()                  // 1. 执行 SQL
               tx.sql("INSERT INTO _migrations...").   // 2. 记录版本
           })
   }
```

### 8.3 多实例安全

`_migrations.version` 列有 `PRIMARY KEY` 约束。如果两个实例同时尝试运行同一迁移，其中一个 INSERT 将因主键冲突失败，另一个成功。重启后两者都能查询到已完成的版本并跳过。

### 8.4 配置

| 符号 | 默认值 | 说明 |
|------|--------|------|
| `freeway.db.migration.enabled` | `true` | 是否启用迁移 |
| `freeway.db.migration.path` | `db/migration/` | 迁移文件路径 |
| `freeway.db.migration.auto` | `true` | 是否在 `@Startup` 时自动运行 |

---

## 9. 多数据源支持

### 9.1 三层设计

| 层级 | 场景 | 实现方式 |
|------|------|----------|
| **单数据库** | 只连一个 DB | 直接注入 `Database` |
| **读写分离** | 主库 + 只读副本 | `@Primary` / `@ReadOnly` marker 注解 |
| **多异构数据库** | 多个独立数据源 | `DbHub` + 配置前缀自动创建 |

### 9.2 @Primary / @ReadOnly

```java
@Primary Database primary;   // 主库 (读写)
@ReadOnly Database replica;  // 副本 (只读)
```

这两个注解在 `freeway-db/src/main/java/com/jujin/freeway/db/annotations/` 下，是简单的 marker 注解，配合 IoC 的 `@Marker` 消歧机制。

### 9.3 DbHub — 多数据源注册中心

通过 YAML 配置前缀自动创建：

```yaml
freeway.db.datasources.analytics.url: jdbc:postgresql://host/analytics
freeway.db.datasources.analytics.username: app
freeway.db.datasources.analytics.password: secret

freeway.db.datasources.logging.url: jdbc:postgresql://host/logging
freeway.db.datasources.logging.username: app
freeway.db.datasources.logging.password: secret
```

运行时获取：

```java
@Inject DbHub dbHub;
Database analytics = dbHub.get("analytics");
Database logging = dbHub.get("logging");
```

`DbHubImpl` 只是一个简单的 `Map<String, Database>` 包装，在 `DbModule` 中通过 `@Contribute` 和 `@Startup` 自动创建。

---

## 10. IoC 集成 — DbModule

`DbModule`（`freeway-db/src/main/java/com/jujin/freeway/db/DbModule.java`，333行）是 Freeway IoC 中的入口 Module，负责：

### 10.1 注册基础设施服务

```
bind():
    RowMapperOverrides.class → RowMapperOverridesImpl

buildDefaultRowMapper():  （条件注入）
    TypeCoercer == null → new DefaultRowMapper()  （独立模式）
    TypeCoercer != null → new DefaultRowMapper(typeCoercer, propertyAccess)  （IoC 模式）
```

### 10.2 注册默认符号配置

```
@Contribute(SymbolProvider.class) @FactoryDefaults
setupDefaultSymbols(config):
    config.add("freeway.db.pool.max-size", 10)
    config.add("freeway.db.pool.min-idle", 2)
    config.add("freeway.db.pool.connection-timeout", "30 s")
    config.add("freeway.db.pool.max-lifetime", "30 m")
    config.add("freeway.db.pool.max-idle-time", "10 m")
    config.add("freeway.db.pool.clean-interval", "1 m")
    config.add("freeway.db.pool.health-check-query", "SELECT 1")
    config.add("freeway.db.pool.health-check-timeout", "5 s")
```

### 10.3 自动执行 Migration

```
@Startup
runMigrations(database, scanner, symbols):
    if (migration.auto == false) return
    new MigrationRunner(database, scanner, path, logger).run()
```

这使得 Migration 在容器 `performRegistryStartup()` 阶段自动执行，数据库在应用代码运行前已是最新结构。

---

## 11. 关键技术决策总结

| 决策 | 选择 | 理由 |
|------|------|------|
| 连接池实现 | 自建 Semaphore + ConcurrentLinkedDeque | 零依赖，257 行覆盖 90% 场景。Virtual Threads 使简单池足够 |
| API 风格 | 同步，链式调用 | Virtual Threads 下同步代码可伸缩，避免响应式复杂度 |
| 映射方式 | Record 构造器 / Bean setter | 充分利用 JDK Record 和 IoC PropertyAccess，不做 ORM |
| 事务传递 | 显式参数传递（留 ScopedValue 预留） | 当前直接用 `TransactionImpl` 对象携带连接 |
| 类型转换 | TypeCoercer（可选注入） | IoC 模式下多步转换链，独立模式下内置 basicCoerce |
| 迁移工具 | 自建 ClassPathScanner | 复用 IoC 基础设施，~100 行覆盖核心场景 |
| SQL 解析 | 简单正则 `[:#]([a-zA-Z_]\w*)` | 不做 AST，保持极简 |
| 命名参数 | `#name` + `:name` 双风格 | `#name` 不冲突 `::` cast，`#` 直接替换为 `?` |
| 批量操作 | PreparedStatement.addBatch | JDK 原生批处理，无需额外抽象 |

---

## 12. 与 Go database/sql 的对照

| Go | freeway-db | 说明 |
|----|------------|------|
| `sql.Open(driver, dsn)` | `new DatabaseBuilder().url(...).build()` | 构建器模式，更丰富的配置 |
| `*sql.DB` | `Database` 接口 | 同样线程安全、长生命周期 |
| `db.Query(sql, args...)` | `db.sql(sql, args...)` | 链式调用，支持命名参数 |
| `db.QueryRow(...).Scan(&x)` | `db.sql(...).one(Type.class)` | 泛型直接返回目标类型 |
| `db.Exec(sql, args...)` | `db.sql(sql, args...).execute()` | DML 操作 |
| `db.Begin()` | `db.beginTransaction()` | 手动事务，支持 try-with-resources |
| `db.Ping()` | `db.ping()` | 健康检查 |
| `db.Stats()` | `db.stats()` → `DatabaseStats` record | 返回池状态 |
| `rows.Scan(&a, &b)` | `DefaultRowMapper` 自动映射 | Record/Bean/简单类型全覆盖 |
| 第三方 `golang-migrate` | 内置 `MigrationRunner` | ClassPathScanner 扫描 + _migrations 表 |
| 多个 `*sql.DB` 实例 | `@Primary`/`@ReadOnly` + `DbHub` | IoC marker 自动绑定 |
