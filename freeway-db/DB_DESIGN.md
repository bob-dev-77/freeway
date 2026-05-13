# freeway-db 设计说明

## 设计哲学

从第一性原理出发，借鉴 Go `database/sql` 的设计，在 Java 25 上构建零外部依赖的数据库访问层。

核心信条：
- **一个概念一个对象** — `Database` 就是数据库句柄，也是连接池，不需要 `DataSource` + `HikariCP` + `JdbcTemplate` 三层分离
- **显式优于隐式** — SQL 是第一公民，不做 JPQL/HQL 抽象，不做懒加载和脏检查
- **约定优于配置** — Record 自动映射列名（snake_case ↔ camelCase），不需要 `@Column` 注解的多数场景
- **利用平台能力** — Virtual Threads 让同步代码可伸缩，ScopedValue 替代 ThreadLocal 传事务上下文，MethodHandle 替代反射做属性访问

## 模块结构

```
freeway-db/
  Database.java          核心接口 — 数据库句柄即连接池
  DatabaseBuilder.java   构建器
  Query.java             查询构建器（命名/位置参数，list/one/execute/stream）
  Transaction.java       事务（lambda 式 + try-with-resources 手动式）
  RowMapper.java         自定义行映射函数式接口
  DatabaseStats.java     池统计 record
  SqlException.java      非受检 SQL 异常
  DbModule.java          IoC 模块
  DbModuleProvider.java  SPI 自动发现
  annotations/
    Primary.java         主库 marker
    ReadOnly.java        只读库 marker
  internal/
    ConnectionPool.java  Semaphore + ConcurrentLinkedDeque 连接池
    PoolConfig.java      池配置 record
    PooledConnection.java JDBC Connection 包装
    DatabaseImpl.java    Database 实现
    QueryImpl.java       Query 实现
    DefaultRowMapper.java Record/Bean/简单类型 自动映射（可选 TypeCoercer+PropertyAccess）
    MigrationRunner.java 轻量 migration 工具（ClassPathScanner 扫描 db/migration/）
    NamedParamParser.java :name → ? 占位符解析
```

## 连接池

### 模型

`Semaphore(maxSize)` 控制准入。借用时先获取许可，再取连接（空闲队列或新建）。归还时连接入队，释放许可。

```
borrow()
  → semaphore.tryAcquire(timeout)
  → idle.poll() → 有且有效则返回
  → idle.poll() → 有但无效则销毁，用已持有的许可重试
  → 无空闲则 createConnection() + total++
  → 失败则 semaphore.release()

release(conn)
  → 有效: idle.offer(conn) + semaphore.release()
  → 无效: destroy(conn) + semaphore.release()
```

后台虚拟线程定期清理过期连接（maxLifetime / maxIdleTime）并维持 minIdle。

### 配置

| 符号 | 默认值 | 说明 |
|------|--------|------|
| `freeway.db.pool.max-size` | 10 | 最大连接数（空闲+活跃） |
| `freeway.db.pool.min-idle` | 2 | 最小空闲连接 |
| `freeway.db.pool.connection-timeout` | 30s | 等待连接超时 |
| `freeway.db.pool.max-lifetime` | 30m | 连接最大存活时间 |
| `freeway.db.pool.max-idle-time` | 10m | 空闲连接最大存活时间 |
| `freeway.db.pool.clean-interval` | 1m | 后台清理间隔 |
| `freeway.db.pool.health-check-query` | SELECT 1 | 连接有效性验证查询 |
| `freeway.db.pool.health-check-timeout` | 5s | 健康检查超时 |

## 查询

### 参数绑定

```java
// 位置参数
db.sql("SELECT * FROM users WHERE status = ? AND age > ?", "active", 18)

// 命名参数
db.sql("SELECT * FROM users WHERE status = :status")
    .param("status", "active")
```

内部 `NamedParamParser` 将 `:name` 转为 JDBC `?` 占位符，保留参数顺序。

### 结果映射

```
简单类型 (String, Long, Integer, BigDecimal, etc.)
  → rs.getObject(1) → coerce()

Record
  → getRecordComponents() → 列名匹配 → coerce → canonical constructor

JavaBean
  → PropertyAccess (MethodHandle) 或 java.beans.Introspector → setter
```

类型转换双路径：

- **IoC 模式**：`TypeCoercer.coerce()`（多步转换链，支持自定义 Coercion），fallback 到内置 basicCoerce
- **独立模式**：内置 basicCoerce（数值拓宽/缩窄、String↔基本类型、JDBC 时间→java.time）

### 列名匹配

自动 `snake_case ↔ camelCase`：

- `user_id` ↔ `userId`
- `created_at` ↔ `createdAt`

先精确匹配（忽略大小写），再尝试 snake_case 转换匹配。

## 事务

### Lambda 式（推荐）

```java
db.transaction(tx -> {
    tx.sql("UPDATE accounts SET balance = balance - :amt WHERE id = :id")
      .param("amt", 100).param("id", fromId).execute();
    tx.sql("UPDATE accounts SET balance = balance + :amt WHERE id = :id")
      .param("amt", 100).param("id", toId).execute();
});
// 正常返回 → 自动 commit
// 抛异常 → 自动 rollback
```

内部通过 `Connection.setAutoCommit(false)` 实现，整个 lambda 共享同一个 JDBC 连接。

### 手动式

```java
try (var tx = db.beginTransaction()) {
    tx.sql("...").execute();
    tx.commit();
}
// 未 commit 的 transaction close 时自动 rollback
```

## Migration

### 文件约定

```
src/main/resources/db/migration/
  V001__create_users.sql
  V002__add_email_column.sql
  V003__create_orders.sql
```

文件名格式：`V<序号>__<描述>.sql`。按文件名字典序执行。

### 执行流程

```
@Startup
  → MigrationRunner.run()
    → create _migrations 表 (version PK, description, executed_at)
    → ClassPathScanner.scan("db/migration/", f -> f.endsWith(".sql"))
    → SELECT version FROM _migrations → 已跑集合
    → 差集排序 → 逐文件 transaction(execute SQL + INSERT INTO _migrations)
```

幂等：已跑过的版本不会重复执行。多实例安全：version 主键唯一约束，并发 INSERT 仅一个成功。

### 配置

| 符号 | 默认值 | 说明 |
|------|--------|------|
| `freeway.db.migration.enabled` | true | 是否启用 |
| `freeway.db.migration.path` | db/migration/ | classpath 扫描路径 |

## 多数据源

### 层级 1：单数据库

`DbModule` 自动配置，零代码。注入 `Database` 即可。

### 层级 2：读写分离

用 marker 注解区分主库和只读库：

```java
// 注入
@Primary  Database primary;
@ReadOnly Database replica;
```

默认 `Database` 自动标记 `@Primary`。用户额外绑定 `@ReadOnly` 的 `Database`。

### 层级 3：多异构数据库

自定义 marker：

```java
@Target({PARAMETER, FIELD}) @Retention(RUNTIME)
@interface Analytics {}

binder.bind(Database.class, AnalyticsDbBuilder.class)
    .withMarker(Analytics.class);
```

### 配置前缀自动创建

通过 `freeway.db.datasources.<name>.*` 配置前缀自动创建多个 `Database` 实例，无需为每个数据源编写 ServiceBuilder。Marker 与数据源名称自动对应。

```yaml
freeway.db.datasources.primary.url: jdbc:postgresql://...
freeway.db.datasources.primary.username: app
freeway.db.datasources.replica.url: jdbc:postgresql://...
freeway.db.datasources.replica.username: ro
```

## 关键技术决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 连接池实现 | 自建 Semaphore+Queue | 零依赖，200 行覆盖 90% 场景。Virtual Threads 使简单池足够 |
| API 风格 | 同步，链式调用 | Virtual Threads 下同步代码可伸缩，避免响应式复杂度 |
| 映射方式 | Record 构造器 / Bean setter | 利用 JDK Record 和 IoC PropertyAccess，不做 ORM |
| 事务传递 | ScopedValue（预留） | 当前用显式参数传递。ScopedValue 未来可替代 |
| 类型转换 | TypeCoercer（可选） | IoC 模式下使用多步转换链，独立模式下内置 basicCoerce |
| 迁移 | 自建 ClassPathScanner | 复用 IoC 基础设施，50 行覆盖核心场景 |
| SQL 解析 | 简单正则 `:name` | 不做 AST，保持轻量 |
| 命名参数 | 仅 `:name` 格式 | 不引入 `?` 和 `:name` 混合的歧义 |

## 与 Go database/sql 的对照

| Go | freeway-db                               |
|----|------------------------------------------|
| `sql.Open(driver, dsn)` | `Database.builder().url(...).build()`    |
| `*sql.DB` | `Database` 接口                            |
| `db.Query(sql, args...)` | `db.sql(sql, args...)`                   |
| `db.QueryRow(sql, args...).Scan(&x)` | `db.sql(sql, args...).one(Type.class)`   |
| `db.Exec(sql, args...)` | `db.sql(sql, args...).execute()`         |
| `db.Begin()` | `db.beginTransaction()`                  |
| `db.Ping()` | `db.ping()`                              |
| `db.Stats()` | `db.stats()` → `DatabaseStats` record    |
| `rows.Scan(&a, &b)` | `DefaultRowMapper` 自动(手工)映射到 Record/Bean |
| 第三方 `golang-migrate` | 内置 `MigrationRunner`                     |
| 多个 `*sql.DB` 实例 | marker 注解 + IoC 绑定                       |
