# freeway-db 结构设计评估 & 重构方案

> 跳出实现层面，从 API 设计、模块耦合、可扩展性维度审视 freeway-db 的结构性问题。

---

## 问题一览

### D1. `DbModule.buildDatabase()` — 14 参数的"上帝方法"

```java
public static Database buildDatabase(
    @Symbol("freeway.db.url") String url,
    @Symbol("freeway.db.username") String username,
    @Symbol("freeway.db.password") String password,
    @Symbol("freeway.db.pool.max-size") int maxSize,
    @Symbol("freeway.db.pool.min-idle") int minIdle,
    @Symbol("freeway.db.pool.connection-timeout") @IntermediateType(TimeInterval.class) int connTimeoutMs,
    @Symbol("freeway.db.pool.max-lifetime") @IntermediateType(TimeInterval.class) int maxLifetimeMs,
    @Symbol("freeway.db.pool.max-idle-time") @IntermediateType(TimeInterval.class) int maxIdleTimeMs,
    @Symbol("freeway.db.pool.clean-interval") @IntermediateType(TimeInterval.class) int cleanIntervalMs,
    @Symbol("freeway.db.pool.health-check-query") String healthQuery,
    @Symbol("freeway.db.pool.health-check-timeout") @IntermediateType(TimeInterval.class) int healthTimeoutMs,
    RegistryShutdownHub shutdownHub,
    TypeCoercer typeCoercer,
    PropertyAccess propertyAccess,
    RowMapperOverrides rowMapperOverrides) { ... }
```

**问题**：
- 每个配置参数都作为独立的 `@Symbol` 注解参数暴露，新增一项配置就需要修改方法签名
- `@IntermediateType(TimeInterval.class)` 对每个 duration 参数重复 5 次
- 同样的配置在 `buildDatabases()` 里通过 `intSymbol()`/`durationSymbol()` 又获取一遍，但用了**不同的获取方式**
- 两处路径的设计不一致：`buildDatabase()` 用 IoC 注入，`buildDatabases()` 手动从 SymbolSource 读取

### D2. 配置双重定义 — 默认值存在两份

`DatabaseBuilder` 的字段默认值：
```java
int maxSize = 10;
int minIdle = 2;
Duration connectionTimeout = Duration.ofSeconds(30);
```

`DbModule.contributeDefaults()` 的符号默认值：
```java
config.add("freeway.db.pool.max-size", 10);
config.add("freeway.db.pool.min-idle", 2);
config.add("freeway.db.pool.connection-timeout", "30 s");
```

**问题**：新增配置项需要同时修改两处。两处可能不一致（已经出现过 — `cleanInterval` 默认值表达不同）。

### D3. `DatabaseBuilder.rowMapper()` 接受实现类而非接口

```java
com.jujin.freeway.db.internal.DefaultRowMapper rowMapper;
```
• 耦合到 `DefaultRowMapper` 具体类  
• `DefaultRowMapper` 在 `internal` 包下，API 不应该暴露内部实现  
• 用户无法替换 RowMapper 策略（例如只想改 type coercion 行为，而不是整行映射）

### D4. `DefaultRowMapper` 不是 IoC 服务

用户可以通过 `@Contribute(RowMapperOverrides.class)` 注册整行映射，但**无法通过 IoC 扩展**：
- 类型转换逻辑（`coerce()` 方法固定 3 层：TypeCoercer → basicCoerce → toString）
- 列名匹配策略（`findColumn()` 固定 snake_case 算法）
- 默认值填充（null → 0/false 固定逻辑）

如果用户只是想让 `BigDecimal` 映射到自定义的 `Money` 类型，也必须写一个完整的 `RowMapper<Money>`，而不能只注册一个 `coercer`。

### D5. `PoolConfig` 不包含 query timeout

`queryTimeoutSeconds` 在 `DatabaseImpl` 里独立存储，不在 `PoolConfig` 中。同一个 `Database` 的配置散落在两个记录里，不必要。

### D6. `DbModule.bind()` 是空方法

```java
public static void bind(ServiceBinder binder) {
    // Services are defined by build*() methods below, not via ServiceBinder.
}
```

IoC 约定是 `bind()` 用于接口→实现的绑定。空 `bind()` 有误导性，而且确实有服务应该通过 bind 注册（如 `RowMapperOverrides`）。

### D7. `buildDatabases()` 中独立的配置解析重复

`buildDatabases()` 自己调 `intSymbol()` 和 `durationSymbol()`，而不是复用 `buildDatabase()` 或 `DatabaseBuilder`。这导致每个 datasource 的构建逻辑与主 database 代码完全独立。

---

## 重构方案

### R1. 引入 `DatabaseConfig` record

使配置从"15 个 setter"变成"一个 record"：

```java
public record DatabaseConfig(
    String url, String username, String password,
    int maxSize, int minIdle,
    Duration connectionTimeout, Duration maxLifetime, Duration maxIdleTime,
    Duration cleanInterval,
    String healthCheckQuery, Duration healthCheckTimeout,
    Duration queryTimeout
) {
    public static Builder builder() { ... }
    
    // 从 SymbolSource + prefix 构建（IoC 模式用）
    public static DatabaseConfig fromSymbols(SymbolSource symbols, String prefix) { ... }
}
```

`DatabaseBuilder` 保留 fluent API，但同时提供 `build(DatabaseConfig)` 重载。

### R2. `DefaultRowMapper` 注册为 IoC 服务

- `DefaultRowMapper` 成为 IoC 服务（通过 `buildDefaultRowMapper()` 注册）
- 用户可以通过 `@Contribute(TypeCoercer.class)` IoC 扩展点注册自定义类型转换
- 保持 `@Contribute(RowMapperOverrides.class)` 作为整行映射覆盖

### R3. `DbModule` 精简

```java
// 之前: 14 个参数
// 之后: 1 个 DatabaseConfig + 必要的 IoC 服务
public static Database buildDatabase(
    DatabaseConfig config,          // IoC 自动注入
    RegistryShutdownHub shutdownHub,
    DefaultRowMapper rowMapper) {   // IoC 服务
    Database db = new DatabaseBuilder()
        .build(config, rowMapper);
    shutdownHub.addRegistryShutdownListener(db::close);
    return db;
}
```

其中 `DatabaseConfig` 本身可被自动构建（IoC 注入）。

### R4. 合并配置默认值

只在 `DatabaseConfig.Builder` 中定义默认值。`DbModule` 不再重复定义。

### R5. `DatabaseBuilder.rowMapper()` 接受接口

```java
// 之前：com.jujin.freeway.db.internal.DefaultRowMapper rowMapper;
// 之后：
private DefaultRowMapper rowMapper;  // DefaultRowMapper 提升为 public 接口

// 如果是 IoC 模式，DbModule 会注入 DefaultRowMapper 并设到 builder
```

---

## 实施优先级

| 优先级 | 重构 | 工作量 | 收益 |
|--------|------|--------|------|
| P0 | R1 `DatabaseConfig` record + `fromSymbols` 工厂 | ~80 行 | 消除 14 参方法，统一配置定义 |
| P0 | R3 `DbModule` 精简 | ~50 行 | 降低认知负担 |
| P1 | R5 `DatabaseBuilder.rowMapper()` 改进 | ~10 行 | API 干净度 |
| P2 | R2 `DefaultRowMapper` IoC 服务化 | ~30 行 | 用户可扩展 |
| P3 | R4 默认值合并 | ~15 行 | 消除双重定义 |
