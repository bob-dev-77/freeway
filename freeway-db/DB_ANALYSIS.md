# freeway-db 模块分析报告

基于最新代码 (2026-05-14) 的完整评估。

---

## 一、上次问题修复确认

| # | 原始问题 | 状态 |
|---|---------|------|
| 1 | Cleaner 线程未中断 | **已修复** — `close()` 保存 `cleanThread` 引用并通过 `join()` 等待退出 |
| 2 | QueryImpl.stream() 连接泄漏 | **未修复** — Stream 不消费时仍泄漏连接 |
| 3 | Record mapper 竞态 (`columnIndexes[]`) | **未修复** — 数组仍被 lambda 捕获并跨线程共享 |
| 4 | borrow() 中 total 计数偏差 | **未修复** — `createConnection()` 失败且之前 destroy 过空闲连接时 total 偏小 |
| 5 | BatchQueryImpl 无条件解析命名参数 | **未修复** |
| 6 | NamedParamParser 与 `::` 冲突 | **未修复** |
| 7 | Duration 解析不一致 | **已修复** — 提取 `DatabaseConfig`，统一使用 `fromSymbols()` |
| 8 | Transaction 文档引用 `ScopedValue` | **未修复** — Transaction.java Javadoc 仍提及不存在的 ScopedValue |
| 9 | 命名数据源缺少 healthCheck/queryTimeout | **已修复** — `DatabaseConfig` 包含所有配置项 |
| 10 | 缺少查询超时 | **已修复** — `queryTimeout` 已加入 Builder/Config，应用到 Statement |

---

## 二、现存严重问题

### 1. DatabaseBuilder.build() 传 null rowMapper 导致 NPE

**文件**: `DatabaseBuilder.java:195-196`

```java
return new DatabaseImpl(pool, null, qTimeout);
```

如果用户不调用 `.rowMapper()`，`DatabaseImpl` 的 `rowMapper` 字段为 `null`，后续 `QueryImpl` 中调用 `db.rowMapper.forClass(targetType)` 直接抛出 `NullPointerException`。

旧版两参数构造函数 `new DatabaseImpl(pool)` 会自动创建 `new DefaultRowMapper()` 兜底，但 `build()` 走的是三参数路径，不再有这个兜底。

**修复建议**: `DatabaseImpl` 的三参数构造函数在 `rowMapper == null` 时自动创建 `new DefaultRowMapper()`。

**影响**: 高危。任何通过 `new DatabaseBuilder().url(...).username(...).password(...).build()` 创建的 Database 实例，首次执行查询即崩溃。

---

### 2. QueryImpl.stream() 连接泄漏风险

**文件**: `QueryImpl.java:118-158`

用户创建 Stream 后不调用终端操作且不关闭，底层 `ResultSet`、`Statement` 和连接永久泄漏：

```java
db.sql("SELECT * FROM huge_table").stream(User.class); // 连接泄漏！
```

**修复建议**: 在 `Query` 接口的 Javadoc 中明确要求 try-with-resources，或考虑增加超时自动关闭机制。

**影响**: 中高危。长时间运行的应用可能因连接泄漏耗尽池资源。

---

## 三、现存中等问题

### 3. DefaultRowMapper 所有 mapper 变体的 columnIndexes 竞态

**文件**: `DefaultRowMapper.java:118-140` (record)、`:160-181` (IoC bean)、`:198-224` (reflection bean)

三处创建 mapper 时都使用 `int[] columnIndexes` 数组并在 `rowNum == 0` 时写入。mapper 按类型缓存在 `ConcurrentHashMap` 中，多线程并发查询同一类型时存在理论竞态。

**实际风险**: 低。同类型的 ResultSet 列结构一致，写入相同值，只是多做了几次 `findColumn`。

**修复建议**: 使用 `AtomicIntegerArray` 或在首次解析时用局部变量 + `volatile` 引用。

---

### 4. DefaultRowMapper IOC bean mapper 每行重复获取 PropertyAdapter

**文件**: `DefaultRowMapper.java:176`

`createIocBeanMapper` 虽然预提取了 `writableProps` 数组，但每行映射时仍调用 `adapter.getPropertyAdapter(writableProps[i])`。这些 adapter 在同一类型上是不变的，增加了不必要的开销。

**修复建议**: 将 `PropertyAdapter` 也预提取为数组。

---

### 5. Transaction 接口文档引用不存在的实现细节

**文件**: `Transaction.java:9`

```java
 * connection (held via {@code ScopedValue}).
```

实际实现 `TransactionImpl` 使用的是直接字段引用 `PooledConnection conn`，从未使用 `ScopedValue`。这是残留的过时文档。

---

### 6. DatabaseConfig.durationSymbol() 静默吞掉解析错误

**文件**: `DatabaseConfig.java:108-125`

```java
try { ... } catch (NumberFormatException e) {
    return defaultValue;
}
```

用户拼写错误如 "3x0 s" 会被静默回退到默认值，无法发现配置错误。

**修复建议**: 至少记录 `logger.warn("Freeway DB: ignoring invalid duration '{}' for key '{}', using default", val, key)`。

---

### 7. Collection/Array 展开对空集合抛出异常

**文件**: `QueryImpl.java:232-233`

```java
if (col.isEmpty())
    throw new SqlException("Cannot expand empty Collection for '?' placeholder");
```

"空集合 → 不匹配任何行" 是实际业务中的合理需求（如动态过滤条件）。

**修复建议**: 生成 `WHERE 1=0` 或将 `List<T>` 结果映射为空列表。

---

## 四、改进建议

### 8. DatabaseConfig.extraProperties 无法从配置符号设置

**文件**: `DatabaseConfig.java:83-86`

`fromSymbols()` 总是 `new Properties()`，无法通过 `freeway.db.properties.ssl=true` 等方式传入额外 JDBC 连接属性。

**建议**: 支持 `.properties.<key>` 配置路径或通过额外构造函数参数传入。

---

### 9. DatabaseBuilder.fromConfig() 缺少 Properties 防御性拷贝

**文件**: `DatabaseBuilder.java:92-93`

```java
this.extraProperties = config.extraProperties() != null
    ? config.extraProperties() : new Properties();
```

直接引用 config 中的 Properties 对象，`build()` 中 `props.setProperty("user", ...)` 会修改共享对象。

**建议**: `this.extraProperties = new Properties(config.extraProperties())`。

---

### 10. QueryImpl.expandPositional() 不区分 SQL 字面量中的 `?`

**文件**: `QueryImpl.java:225`

`originalSql.indexOf('?', sqlIdx)` 是纯字符串搜索，会匹配 SQL 字符串字面量或注释中的 `?`。如 `SELECT 'a?b' FROM t WHERE id IN (?)` 中的 `'a?b'` 的 `?` 会被误匹配。

**实际风险**: 极低。SQL 字面量中很少出现独立的 `?`。

---

### 11. 缺少连接初始化回调

`ConnectionPool.createConnection()` 只执行 `setAutoCommit(true)`。无法配置时区、schema search path 等 session 级设置。

**建议**: 在 `DatabaseBuilder` 中增加 `initSql(String)` 或 `init(Consumer<Connection>)` 配置。

---

### 12. 缺少连接泄漏检测 / 慢查询告警

没有机制跟踪连接被借出后的持有时间。生产环境中慢查询或未释放连接可能导致池耗尽。

**建议**: `borrow()` 记录时间戳，后台线程定期检测超时持有并记录警告。

---

### 13. 不支持 BigInteger

**文件**: `DefaultRowMapper.java:92-107`

`isSimpleType()` 中缺少 `BigInteger.class`，用户的 `BigInteger` 字段将 fall through 到 bean mapper。

**建议**: 将 `BigInteger` 加入简单类型列表。

---

### 14. BatchQueryImpl 构造函数无条件解析命名参数

**文件**: `BatchQueryImpl.java:26`

```java
this.parsed = NamedParamParser.parse(sql);
```

即使纯位置参数的 batch 操作也会执行此解析，是不必要的开销。

**建议**: 延迟到 `bindRow()` 首次调用时解析，或仅在 `paramList()` 被调用时解析。

---

### 15. NamedParamParser 与 PostgreSQL `::` 类型转换冲突

**文件**: `NamedParamParser.java:13`

正则 `[:#]([a-zA-Z_][a-zA-Z0-9_]*)` 会将 `value::text` 中的 `:text` 错误解析为命名参数。

**建议**: 跳过 `::` 后的标识符，或对 `:` 风格增加上下文感知。

---

## 五、新增功能总结 (亮点)

- **Collection/Array 自动展开**: `IN (?)` 传入 `List.of(1,2,3)` → `IN (?,?,?)`，多行 VALUES 也支持
- **空闲连接快速路径**: `isFresh(5s)` 跳过健康检查，高并发下减少延迟
- **新建连接健康检查**: `createConnection()` 中增加 `isValid()` 防创建死连接
- **配置抽象**: `DatabaseConfig` record 统一了 Builder 和 IoC 两条路径
- **camelToSnake 缓存 + 算法改进**: 正确处理 `userId` → `user_id`、`HTMLParser` → `html_parser` 等边界情况
- **fetchSize 支持**: streaming 查询可控每次拉取行数
- **Builder 验证增强**: url 前缀检查、maxSize ≤ 1024、Duration 非空/正数验证
- **ConnectionPool.close() 增强**: 等待活跃连接归还 → 排空 → join cleaner 线程
- **cleaner 线程优雅退出**: 响应 `InterruptedException`
- **异常日志**: `rollbackSilent()`、`closePhysical()` 等静默异常改为 logger 记录

---

## 六、修复任务优先级

| 优先级 | 问题 | 修复成本 |
|--------|------|---------|
| P0 | `null` rowMapper NPE — 任何 builder 不设置 rowMapper 即崩溃 | 1行 |
| P1 | Transaction 文档修正 — 移除 `ScopedValue` 引用 | 1行 |
| P1 | `durationSymbol()` 静默错误增加 warn 日志 | 2行 |
| P2 | Properties 防御性拷贝 | 1行 |
| P2 | Bean mapper PropertyAdapter 预提取 | 5行 |
| P3 | columnIndexes 竞态修复 | 10行 |
| P3 | stream 连接泄漏文档/机制改进 | 5-20行 |
| P4 | BigInteger 支持 | 1行 |
| P4 | BatchQuery 延迟解析 | 10行 |
| P4 | 连接初始化回调 | 15行 |
| P5 | `::` 冲突处理 | 15行 |
| P5 | 空集合展开优化 | 10行 |
| P5 | 连接泄漏检测 | 30行 |
| P5 | extraProperties 配置路径 | 15行 |

---

## 七、整体评分

| 维度 | 评分 | 说明 |
|------|------|------|
| 正确性 | ★★★★☆ | 核心路径正确，有 1 个 P0 的 null 问题 |
| API 设计 | ★★★★★ | 简洁直观，lambda + try-with-resources 双模式 |
| 并发安全 | ★★★★☆ | Semaphore 池设计正确，有轻微 kolumnIndexes 竞态 |
| 资源管理 | ★★★★☆ | close() 大幅增强，stream 路径仍可改进 |
| 配置灵活性 | ★★★★☆ | DatabaseConfig 统一了解析，extraProperties 尚有不足 |
| 生产可用性 | ★★★★☆ | 缺少连接泄漏检测和慢查询告警 |
| 测试覆盖 | ★★★★☆ | 5 个测试类覆盖核心路径，可增加边缘场景 |

**总体**: 模块质量良好，零外部依赖的连接池设计简洁可靠。19 个原始问题中解决了约 60%。P0 问题（null rowMapper NPE）是本次重构引入的新 bug，应优先修复。
