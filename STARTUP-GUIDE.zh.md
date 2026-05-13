# @Startup 开发者指南

> Freeway 容器的"发令枪"——所有服务就绪后，Module 启动外部资源的最终信号。

---

## 目录

1. [功能概述](#1-功能概述)
2. [执行时机与生命周期位置](#2-执行时机与生命周期位置)
3. [核心特性](#3-核心特性)
4. [使用场景与代码示例](#4-使用场景与代码示例)
5. [@Startup 与 build*/bind 的职责边界](#5-startup-与-buildbind-的职责边界)
6. [最佳实践](#6-最佳实践)
7. [常见陷阱](#7-常见陷阱)
8. [FAQ](#8-faq)

---

## 1. 功能概述

`@Startup` 是一个**方法级注解**，标记 Module 中的某个方法为"启动方法"。它由 `Registry.performRegistryStartup()` 在所有服务注册完成后统一触发执行。

```java
@Target(METHOD)
@Retention(RUNTIME)
@Documented
public @interface Startup {}
```

**核心语义：** 注解本身零配置——没有优先级、没有名称、没有属性。它是一个纯粹的"触发点"标记，设计上刻意保持极简。

---

## 2. 执行时机与生命周期位置

### 2.1 完整启动时序

```
Registry.builder()
    .addModule(ModuleA.class)
    .addModule(ModuleB.class)
    .build()                           ← 此时所有 Module 处理完毕
    ↓
Registry.performRegistryStartup()     ← 显式触发启动
    ↓
  ├─ 1. EagerLoad 收集          ← 找出所有 @EagerLoad 的服务
  ├─ 2. 实例化 EagerLoad 服务    ← 迫使懒加载服务完成初始化
  ├─ 3. 执行 @Startup 方法       ← ◄── 本指南关注的核心
  │      ├─ ModuleA.startup()
  │      ├─ ModuleB.startup()
  │      └─ ...
  ├─ 4. 执行 RegistryStartup Runnable
  ├─ 5. cleanupThread()          ← 清理线程局部状态
  ↓
Registry 进入就绪状态
```

### 2.2 关键时间点

当 `@Startup` 执行时，以下条件全部满足：

| 条件 | 状态 |
|------|------|
| 所有 Module 的 `build*()` / `bind()` | ✅ 已完成 |
| 所有 `@Advise` 增强器 | ✅ 已编织 |
| 所有 `@Contribute` 贡献 | ✅ 已合并 |
| 所有 `@EagerLoad` 服务 | ✅ 已实例化 |
| 所有服务（含懒加载） | ✅ 可按需查找/注入 |
| Registry 对外查询 | ✅ 已可用 |

**这是 `@Startup` 和 Module 构造阶段方法的本质区别：build/advise/contribute 执行时，其他 Module 的服务可能还未注册；而 `@Startup` 执行时，一切就绪。**

### 2.3 一次性保证

`performRegistryStartup()` 由 `OneShotLock` 保证**只执行一次**。执行完毕后 startups 列表被 clear，不可重入。

---

## 3. 核心特性

### 3.1 参数自动注入（方法注入）

`@Startup` 方法支持声明参数，Freeway 会在调用时自动注入：

```java
@Startup
public static void start(
    SomeService svc,                        // 从 Registry 按类型查找
    Logger logger,                          // 基于 Module 类名自动创建
    ServiceLocator locator,                 // 程序化查找入口
    RegistryShutdownHub shutdownHub,        // 注册关闭回调
    @Symbol("server.port") int port,        // 符号解析配置值
    @Symbol("server.host") String host      // 字符串配置值
) { ... }
```

可注入的参数类型包括：

| 类型 | 说明 |
|------|------|
| **已注册的服务** | 按类型从 Registry 中查找 |
| `Logger` | 基于 Module 类名自动创建 |
| `ServiceLocator` | 程序化查找入口 |
| `RegistryShutdownHub` | 注册优雅关闭回调 |
| `@Symbol` 注解参数 | 解析外部配置（YAML/properties/环境变量/命令行） |

这是 Tapestry/Guice 风格的**方法注入**——`@Startup` 方法本身就是一个注入点，依赖声明在参数列表中，清晰可见。

### 3.2 容错性

`RegistryStartup.run()` 执行时，如果某个 startup 方法抛出异常，**不会中断整个启动流程**：

```java
for (Runnable r : configuration) {
    try {
        r.run();
    } catch (RuntimeException ex) {
        logger.error("An exception occurred during startup: {}", ...);
    }
}
```

这意味着：
- Module A 的 `@Startup` 失败 → Module B 的 `@Startup` **照常执行**
- 但如果你依赖 Module A startup 成功后创建的资源，需要在方法内自行保证

### 3.3 无执行顺序保证

多个 Module 的 `@Startup` 方法按 Module 注册顺序执行，但**不提供细粒度的排序机制**。如果 A 必须在 B 之前完成，有两种方案：

1. **方案一（推荐）：** 重新审视模块依赖设计，确保不依赖顺序
2. **方案二：** 通过 `@Contribute(RegistryStartup.class)` 的 ordered 配置显式控制

---

## 4. 使用场景与代码示例

### 4.1 启动 HTTP Server（权威示例）

这是 `@Startup` 最经典的使用场景——`WebModule` 启动内置服务器：

```java
@Startup
public static void startWebServer(
        RouteRegistry routeRegistry,
        JsonCodec jsonCodec,
        HttpFilterChain filterChain,
        ExceptionMapperChain exceptionMapperChain,
        RegistryShutdownHub shutdownHub,
        Logger logger,
        @Symbol("freeway.server.port") int port,
        @Symbol("freeway.server.host") String host,
        @Symbol("freeway.server.backlog") int backlog) throws Exception {

    // RouteRegistry 此时已被所有 Module 的 @Contribute 填满
    var server = HttpServer.create(new InetSocketAddress(host, port), backlog);
    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

    server.createContext("/", exchange -> {
        // 使用 routeRegistry 分发请求...
    });

    server.start();

    // 注册关闭回调，确保优雅停止
    shutdownHub.addRegistryShutdownListener(() -> server.stop(2));
}
```

**为什么必须是 @Startup？** 启动 HTTP Server 需要 `RouteRegistry`、`HttpFilterChain`、`ExceptionMapperChain` 等基础设施已被所有 Module 的 `@Contribute` 填满。只有 `@Startup` 能确保这个前提。

### 4.2 缓存预热

系统启动后，预填充热点数据到缓存：

```java
@Startup
public static void warmUpCache(
        CacheService cache,
        @Symbol("app.cache.warmup.enabled") boolean enabled,
        Logger logger) {
    if (!enabled) return;

    logger.info("Starting cache warmup...");
    cache.preload("hot_keys");
    cache.preload("user_sessions");
    logger.info("Cache warmup completed");
}
```

### 4.3 启动后台轮询/调度任务

```java
@Startup
public static void startScheduler(
        DataSyncService syncService,
        RegistryShutdownHub shutdownHub,
        Logger logger) {

    var executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "data-sync-worker");
        t.setDaemon(true);
        return t;
    });

    executor.scheduleAtFixedRate(() -> {
        try {
            syncService.poll();
        } catch (Exception e) {
            logger.error("Data sync failed", e);
        }
    }, 0, 30, TimeUnit.SECONDS);

    // 重要：注册关闭回调
    shutdownHub.addRegistryShutdownListener(executor::shutdown);
}
```

### 4.4 启动健康自检

```java
@Startup
public static void healthCheck(
        DataSource dataSource,
        QueueConnection queueConnection,
        Logger logger) {

    // 数据库连通性检查
    try (var conn = dataSource.getConnection()) {
        logger.info("Database connection verified: {}",
            conn.getMetaData().getURL());
    } catch (Exception e) {
        logger.warn("Database not available at startup, will retry later");
        // 不抛异常——容错机制会吞掉，但 warn 日志会保留
    }

    // 消息队列连通性检查
    try {
        queueConnection.ping();
        logger.info("Message queue connection verified");
    } catch (Exception e) {
        logger.warn("Message queue not available at startup");
    }
}
```

### 4.5 注册外部服务发现

```java
@Startup
public static void registerServiceDiscovery(
        ServiceRegistry serviceRegistry,
        RegistryShutdownHub shutdownHub,
        @Symbol("app.service.name") String serviceName,
        @Symbol("app.service.port") int port) {

    // 向注册中心注册当前服务实例
    serviceRegistry.register(serviceName, InetAddress.getLocalHost(), port);

    // 关闭时自动注销
    shutdownHub.addRegistryShutdownListener(() ->
        serviceRegistry.unregister(serviceName));
}
```

### 4.6 初始化状态机/规则引擎

```java
@Startup
public static void initializeRules(
        RuleEngine ruleEngine,
        Logger logger) {

    logger.info("Loading business rules...");
    ruleEngine.loadRules("classpath:rules/default-rules.drl");
    ruleEngine.loadRules("classpath:rules/override-rules.drl");
    logger.info("Loaded {} rules", ruleEngine.ruleCount());
}
```

---

## 5. @Startup 与 build/bind 的职责边界

### 5.1 对比总览

| 维度 | `build*()` / `bind()` | `@Startup` |
|------|----------------------|------------|
| 执行阶段 | Module 处理阶段（Registry 仍在构造中） | Registry **完全构建后** |
| 服务可见性 | 仅当前 Module 的绑定可见 | **所有 Module** 的所有服务都可用 |
| `@Contribute` 数据 | 当前 Module 的贡献可见 | **所有 Module** 的贡献已合并 |
| 适用任务 | 注册服务、定义顾问、贡献配置 | 启动外部资源、执行后置动作 |
| 错误影响 | 导致 Module 构建失败 | **不会中断**整体启动（容错） |
| 参数注入 | ✅ 支持 | ✅ 支持 |
| 异常传播 | 向上抛出 | 日志记录后继续 |

### 5.2 选择速查表

```
任务需要依赖其他 Module 的服务或配置吗？
  └─ 是 → @Startup
  └─ 否 → 任务是注册新东西吗？
            ├─ 注册服务 → build*/bind()
            ├─ 增强服务 → advise*()/@Advise
            ├─ 贡献配置 → contribute*()/@Contribute
            └─ 启动外部资源 → @Startup
```

### 5.3 进阶：@Startup 启动的两个阶段

实际上，`@Startup` 的启动被拆成两个概念层次：

1. **方法级 @Startup**——Module 中标记的 `@Startup` 方法，由 `performRegistryStartup()` 统一调度
2. **RegistryStartup Runnables**——通过 `@Contribute(RegistryStartup.class)` 注册的 Runnable，在 @Startup 方法之后执行

两者都在 `performRegistryStartup()` 中触发，但顺序是：**先 methods，后 runnables**。

---

## 6. 最佳实践

### 6.1 声明式参数优先于程序化查找

```java
// ✅ 推荐：参数列表声明依赖，清晰透明
@Startup
public static void start(MyService svc, Logger log) { ... }

// ❌ 不推荐：方法体内程序化查找，隐藏了依赖
@Startup
public static void start() {
    var svc = registry.getService(MyService.class);  // registry 从哪来？
    var log = LoggerFactory.getLogger(...);
    ...
}
```

方法注入的优点：
- 依赖**显式声明**在方法签名中
- 编译器可检查类型
- IDE 支持导航到依赖定义

### 6.2 始终注册关闭回调

如果 `@Startup` 方法启动了线程、连接池或 Server，务必通过 `RegistryShutdownHub` 注册对应的关闭回调：

```java
@Startup
public static void start(RegistryShutdownHub hub) {
    var executor = Executors.newScheduledThreadPool(4);
    // ... 启动工作 ...

    hub.addRegistryShutdownListener(() -> {
        executor.shutdown();
        try { executor.awaitTermination(5, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    });
}
```

`RegistryShutdownHub` 与 JVM `Runtime.getRuntime().addShutdownHook` 配合构成双层保障。

### 6.3 方法应幂等

虽然 `performRegistryStartup()` 只执行一次，但好的做法是让 `@Startup` 方法本身也幂等——便于测试中重复构建 Registry 场景：

```java
private static volatile boolean started = false;

@Startup
public static void start(Logger log) {
    if (started) return;
    started = true;
    // ... 启动逻辑 ...
}
```

### 6.4 异常应内部消化或转为日志

由于 `@Startup` 的容错机制会吞异常继续执行，如果你依赖某个 startup 成功后的状态，应在方法内处理干净：

```java
@Startup
public static void start(Logger log) {
    try {
        riskyOperation();
    } catch (Exception e) {
        log.error("Startup failed, but continuing", e);
        // 设置一个标志位，让后续操作感知到失败
        failed.set(true);
    }
}
```

### 6.5 不要做耗时操作

`@Startup` 是同步执行的——它会阻塞 `performRegistryStartup()` 的返回。如果你的启动任务耗时较长（如大量数据加载），考虑：
- 将耗时任务放到后台线程执行
- 通过 `RegistryShutdownHub` 确保 Registry 关闭时后台线程也终止

```java
@Startup
public static void start(RegistryShutdownHub hub, Logger log) {
    Thread.ofVirtual().start(() -> {
        try {
            longRunningInit();
        } catch (Exception e) {
            log.error("Background init failed", e);
        }
    });
}
```

### 6.6 测试 @Startup

```java
@Test
void testStartup() {
    // 构建 Registry
    var registry = Registry.builder()
        .addModule(MyModule.class)
        .build();

    // 显式触发 startup
    registry.performRegistryStartup();

    // 验证 startup 效果
    assertTrue(server.isRunning());
    assertEquals(8080, server.port());
}
```

---

## 7. 常见陷阱

### 陷阱 1：在 build*() 中依赖其他 Module 的服务

```java
// ❌ 危险：build*() 执行时，其他 Module 的服务可能尚未注册
public Server buildServer(Registry registry) {
    var routes = registry.getService(RouteRegistry.class); // 可能为空！
    return new HttpServer(routes);
}
```

```java
// ✅ 正确：将启动逻辑放到 @Startup 中
public Server buildServer() {
    return new HttpServer(); // 先创建，不依赖外部状态
}

@Startup
public static void start(Server server, RouteRegistry routes) {
    server.setRoutes(routes.getAll()); // 此时 routes 已被填满
    server.start();
}
```

### 陷阱 2：假设 @Startup 之间的顺序

```java
// ❌ 危险：Module A 和 B 的注册顺序可能变化
// Module A
@Startup public static void initDatabase() { ... }

// Module B
@Startup public static void loadData() {
    // 假设 initDatabase() 已经执行完毕
}
```

```java
// ✅ 正确：在单个 startup 方法中处理依赖链
@Startup
public static void initAll(Logger log) {
    initDatabase();
    loadData();
    log.info("All startup tasks completed");
}
```

### 陷阱 3：@Startup 方法抛异常导致资源泄漏

```java
// ❌ 危险：创建了连接，但后续抛异常，连接未关闭
@Startup
public static void start(DataSource ds) {
    var conn = ds.getConnection();  // 如果后面抛异常，conn 泄漏
    riskyOperation();
    conn.close();
}
```

```java
// ✅ 正确：try-with-resources 保护
@Startup
public static void start(DataSource ds) {
    try (var conn = ds.getConnection()) {
        riskyOperation();
    }
}
```

### 陷阱 4：误用 @Startup 做服务注册

```java
// ❌ 错误用法：注册服务不应该在 @Startup 中做
@Startup
public static void register(ServiceBinder binder) {
    binder.bind(X.class, XImpl.class);
    // → 此时 Registry 已构建完成，bind() 无效！
}
```

```java
// ✅ 正确：服务注册在 Module 构造阶段完成
public static void bind(ServiceBinder binder) {
    binder.bind(X.class, XImpl.class);
}
```

---

## 8. FAQ

**Q: 一个 Module 可以定义多个 `@Startup` 方法吗？**

A: 可以。它们按方法发现顺序依次执行。但建议每个 Module 只保留一个 `@Startup` 方法，将多个操作组织在一个方法中，使启动逻辑更可控。

**Q: `@Startup` 方法可以是 private 吗？**

A: 可以。Freeway 通过反射调用，访问修饰符不影响执行。但建议至少用 package-private，便于单元测试直接调用。

**Q: `@Startup` 方法和 `RegistryStartup` Runnable 有什么区别？**

A: `@Startup` 是 Module 上的方法级注解，由 Freeway 自动发现和执行。`RegistryStartup` Runnable 是通过 `@Contribute(RegistryStartup.class)` 注册的，适用于框架基础设施的启动信号。两者都在 `performRegistryStartup()` 中触发，但 `@Startup` methods 先执行，Runnables 后执行。

**Q: 如何测试某个 `@Startup` 方法抛异常的场景？**

A: 构建 Registry → 执行 `performRegistryStartup()` → 验证其他服务仍可用：

```java
@Test
void testStartupFailureIsolation() {
    var registry = Registry.builder()
        .addModule(ModuleA.class)   // 这个会抛异常
        .addModule(ModuleB.class)   // 这个不会
        .build();

    registry.performRegistryStartup();  // 不抛异常

    var svc = registry.getService(ModuleB.ServiceB.class);
    assertNotNull(svc);
}
```

**Q: `@Startup` 支持异步吗？**

A: `@Startup` 方法是同步调用的。如果需要异步，在方法体内自行启动线程（参考 6.5 最佳实践）。

---

> **一句话总结：** `@Startup` 是 Freeway Module 生命周期中"万事俱备，请开始行动"的最终信号——所有服务就绪后，它给 Module 一个机会去启动那些依赖完整容器状态的外部资源。
