# Freeway IoC 容器核心架构深度剖析

> 本文档基于 freeway-ioc 模块源码分析，版本 1.0.0，JDK 25+。

---

## 1. 入口：Registry — 整个容器的门面

`Registry` 扩展了 `ServiceLocator`，是 IoC 容器的最高抽象。位于 `freeway-ioc/src/main/java/com/jujin/freeway/ioc/Registry.java`。

### 核心方法

| 方法 | 作用 |
|------|------|
| `getService(id, interface)` | 按唯一 ID 获取服务代理 |
| `getService(interface)` | 按接口类型获取唯一服务 |
| `getService(interface, markerTypes)` | 按接口 + Marker 注解获取服务 |
| `getServices(interface)` | 获取某个接口的所有实现 |
| `autobuild(Class)` | 通过构造函数注入自动创建任意对象 |
| `getObject(type, annotationProvider)` | 通过 `ObjectInjector` 间接获取对象 |
| `proxy(interface, implClass)` | 创建带自动重载的懒加载代理 |
| `performRegistryStartup()` | 触发所有 `@Startup` 方法 |

### 构建方式

通过 `Registry.Builder` 构建：

```java
// 方式一：显式指定 Module 类
Builder.startAndBuild(MyModule.class, OtherModule.class)

// 方式二：JDK SPI 自动发现 + 额外 Module
Builder.spiAndBuild(MyModule.class)
```

内部构建流程（`RegistryImpl` 构造方法，`freeway-ioc/src/main/java/com/jujin/freeway/ioc/internal/RegistryImpl.java:120`）：

1. 收集所有 `ModuleDefinition`
2. 为每个定义创建 `ModuleImpl` 实例
3. 扫描服务定义，注册到 `serviceIdToModule` 映射
4. 注册内置服务（`PerthreadManager`、`LoggerSource`、`JdkProxyFactory` 等）
5. 验证所有 `@Contribute` 的目标服务是否存在
6. 设置 `ServiceConfigurationListener`

---

## 2. Module 体系 — 容器的组成单元

每个 Module 是一个 Java class，通过**方法命名约定 + 注解**定义其能力。扫描逻辑位于 `DefaultModuleDefinition`（`freeway-ioc/src/main/java/com/jujin/freeway/ioc/internal/DefaultModuleDefinition.java:94`）。

### Module 的五种方法类型

| 方法前缀 | 职责 | 注解 | 说明 |
|----------|------|------|------|
| `build*()` | **服务定义** | `@Scope`、`@EagerLoad`、`@Marker`、`@ServiceId`、`@PreventServiceDecoration` | 方法的返回值类型即服务接口 |
| `advise*()` | **AOP 拦截** | `@Advise(TargetService.class)` | 方法参数接收 `MethodAdviceReceiver`，织入横切逻辑 |
| `contribute*()` | **插件扩展** | `@Contribute(XxxService.class)`、`@Order`、`@FactoryDefaults`、`@Optional` | 向目标服务的配置注入内容 |
| `bind()` | **接口绑定** | — | 接收 `ServiceBinder` 参数，快捷绑定接口→实现类 |
| `startup*()` | **启动回调** | `@Startup` | 容器就绪后触发，所有服务可用 |

### 五种方法的执行时机

```
Registry.builder().addModule(ModuleA).build()
    ↓
DefaultModuleDefinition 扫描 module class
    ├─ 识别 build*() 方法 → ServiceDefinition
    ├─ 识别 bind() 方法 → ServiceBinder → ServiceDefinition
    ├─ 识别 advise*() 方法 → AdvisorDefinition
    ├─ 识别 contribute*() 方法 → ContributionDef
    └─ 识别 @Startup 方法 → StartupDef
    ↓
RegistryImpl 构造完成（服务可查询）
    ↓
performRegistryStartup()
    ├─ @EagerLoad 服务强制实例化
    ├─ @Startup 方法依次执行
    └─ RegistryStartup Runnables 执行
```

### ModuleDefinition 接口

```java
public interface ModuleDefinition {
    Set<String> getServiceIds();
    ServiceDefinition getServiceDef(String serviceId);
    Set<ContributionDef> getContributionDefs();
    Class<?> getBuilderClass();
    String getLoggerName();
    default Set<AdvisorDefinition> getAdvisorDefs();
    default Set<StartupDef> getStartups();
}
```

### DefaultModuleDefinition 扫描算法

1. 遍历 Module class 的所有 `public` 方法
2. 方法名以 `build` 开头 → `ServiceDefinition`（带有 `@Scope`、`@EagerLoad` 等配置）
3. 方法名为 `bind` 且接收 `ServiceBinder` 参数 → 创建 `ServiceBinderImpl`
4. 方法上有 `@Advise` 注解 → `AdvisorDefinition`
5. 方法上有 `@Contribute` 注解 → `ContributionDef`（支持 `@Order`、`@Optional`）
6. 方法上有 `@Startup` 注解 → `StartupDef`

---

## 3. 服务生命周期 — bind → build → proxy → create

### 3.1 服务注册流程

```
bind() 方法调用
    ↓
ServiceBinderImpl（freeway-ioc/.../internal/ServiceBinderImpl.java）
    → 解析接口类、实现类、Marker、Scope 等
    → ServiceDefAccumulator.addServiceDef()
    ↓
ServiceDefinitionImpl（freeway-ioc/.../internal/ServiceDefinitionImpl.java）
    └─ 封装：serviceInterface, serviceImplementation, serviceId,
       markers, scope, eagerLoad, preventDecoration, ObjectCreatorStrategy
    ↓
RegistryImpl 构造
    → serviceIdToModule[serviceId] = module
    → markerToServiceDef[marker].add(serviceDef)
    → serviceDefs.add(serviceDef)
```

### 3.2 服务获取流程

```
ServiceLocator.getService(id, interface)
    → RegistryImpl.getService()
        → ModuleImpl.getService()（freeway-ioc/.../internal/ModuleImpl.java）
            → services map 缓存检查
            → JdkProxyFactory 创建 JDK 动态代理
                ↓
首次方法调用触发：
    → ServiceLifecycle.createService(resources, creator)
        ├─ SingletonServiceLifecycle：调用 creator.create()
        └─ PerThreadServiceLifecycle：每个线程独立实例
            ↓
        → AdvisorStackBuilder.create()（freeway-ioc/.../internal/AdvisorStackBuilder.java）
            → delegate.create()（创建裸服务实例）
            → registry.findAdvisorsForService(serviceDef)
            → interceptor.createBuilder(interface, instance, ...)
            → 每个 advisor 调用 advise(builder)
            → builder.build() → 返回 AOP 代理
```

### 3.3 服务创建 — ConstructorServiceCreator

对于通过 `bind()` 绑定或 `@Autobuild` 标注的服务，使用 `ConstructorServiceCreator`（`freeway-ioc/src/main/java/com/jujin/freeway/ioc/internal/ConstructorServiceCreator.java`）：

```
ConstructorServiceCreator.create()
    → getPlan() → InternalUtils.createConstructorConstructionPlan()
        → InjectionResourcesBuilder：收集所有注入资源
        → 解析构造函数参数（最大 public 构造函数优先）
        → 对每个参数调用 ObjectInjector.inject()
        → 构造函数反射调用
        → 返回实例
```

---

## 4. 依赖注入体系 — ObjectInjector + ServiceProvider 责任链

### 4.1 ObjectInjector 接口

```java
public interface ObjectInjector {
    <T> T inject(
        Class<T> objectType,
        AnnotationProvider annotationProvider,  // 字段/参数上的注解
        ServiceLocator locator,                // 上下文定位器
        boolean required                       // 是否必须
    );
}
```

### 4.2 执行流程（ObjectInjectorImpl，freeway-ioc/.../internal/ObjectInjectorImpl.java）

```
ObjectInjectorImpl.inject(type, annotations, locator, required)
    ↓ OperationTracker 包装
    ↓
    遍历 ServiceProvider 链（按顺序，第一个非 null 胜出）：
    ├─ [0] StaticServiceProvider<OperationTracker> ← 内置，永远最高优先级
    ├─ [1] ServiceProvider A（@Symbol → 替换 ${xxx} 占位符）
    ├─ [2] ServiceProvider B（@Local → 当前模块作用域）
    ├─ [3] ServiceProvider C（自定义贡献的 Provider）
    └─ [4] ...
    ↓ 全部返回 null 且 required=true？
    → locator.getService(type)
        → 按接口类型查找唯一服务
        → 找不到 → 抛异常
```

### 4.3 ServiceProvider 接口

```java
public interface ServiceProvider {
    <T> T resolve(
        Class<T> objectType,
        AnnotationProvider annotationProvider,
        ServiceLocator locator
    );
}
```

每个 `ServiceProvider` 负责解析某种特定来源的对象，通过 `@Contribute(ServiceProvider.class)` 注入 IoC 容器。

### 4.4 注入支持的注解

| 注解 | 作用 |
|------|------|
| `@Inject` | 标注字段或构造函数参数需要自动装配 |
| `@Symbol` | 注入从符号源解析的配置值，如 `${app.port}` |
| `@Local` | 注入当前 Module 作用域内的服务 |
| `@Value` | 注入默认值 |
| `@ServiceId` | 按 ID 区分同一接口的多个实现 |
| `@Primary` | 标注同一接口的主实现 |
| `@Marker` | 用于消歧的标记注解 |
| `@Optional` | 注入可选（null 而非失败） |
| `@PostInjection` | 注入完成后回调 |

### 4.5 构造参数注入解析链

`@Inject` 的双源设计：

1. **模块内源**：优先从当前 Module 的 `build` 方法参数获取
2. **IoC 容器源**：从全局 Registry 中按类型查找

选择最长的 public 构造函数（最大参数数量），每个参数按上述责任链解析。

---

## 5. AOP 拦截机制 — Advisor + MethodAdviceReceiver

### 5.1 核心接口

```java
public interface ServiceAdvisor {
    void advise(MethodAdviceReceiver receiver);
    // receiver.adviseMethod(method, advice) 织入具体方法
}

public interface MethodAdvice {
    void advise(MethodInvocation invocation);
    // invocation.proceed() 继续调用链
    // invocation.getReturnValue() 获取返回值
}
```

### 5.2 Advisor 发现与匹配

Module 中带 `@Advise(ServiceInterface.class)` 的方法被扫描为 `AdvisorDefinition`。

在 `ModuleImpl.findMatchingServiceAdvisors(serviceDef)` 中匹配：
```
对每个 AdvisorDefinition：
    → 检查 @Advise 标注的目标类型是否与 serviceDef 的接口匹配
    → 匹配则加入结果集
```

### 5.3 AdvisorStackBuilder 组装流程

```java
AdvisorStackBuilder.create()
    → delegate.create()                                     // 创建裸服务实例
    → registry.findAdvisorsForService(serviceDef)            // 查找匹配的 advisor
    → interceptor.createBuilder(interface, instance, def)    // 创建拦截器构建器
    → 对每个 advisor:
        advisor.advise(builder)
        // 内部调用 builder.adviseMethod(method, methodAdvice)
    → builder.build()                                        // 返回 AOP 代理对象
```

### 5.4 内置 Advisor

| Advisor | 位置 | 作用 |
|---------|------|------|
| `LazyAdvisorImpl` | `freeway-ioc/.../internal/LazyAdvisorImpl.java` | 将返回接口的方法包装为 **Thunk（延迟对象）**。接口方法首次被调用时才真正触发 `proceed()` |
| `OperationAdvisorImpl` | `freeway-ioc/.../internal/OperationAdvisorImpl.java` | 将 `@Operation` 标记的方法纳入 `OperationTracker` 跟踪，记录调用耗时和上下文 |
| `LoggingAdvisorImpl` | `freeway-ioc/.../internal/LoggingAdvisorImpl.java` | 向服务方法注入日志能力 |

### 5.5 Thunk 延迟加载原理

`LazyAdvisorImpl.filter()` 判断哪些方法需要 thunk 化：
- 方法不能标注 `@NotLazy`
- 返回类型必须是接口
- 所有异常必须是 `RuntimeException` 的子类

Thunk 创建：
```java
ObjectCreator<?> deferred = () -> {
    invocation.proceed();
    return invocation.getReturnValue();
};
ObjectCreator<?> cachingObjectCreator = new CachingObjectCreator<>(deferred);
Object thunk = thunkCreator.createThunk(thunkType, cachingObjectCreator, description);
invocation.setReturnValue(thunk);
```

---

## 6. 贡献机制 — @Contribute 插件扩展

### 6.1 概念

每个 Module 上的 `@Contribute(XxxService.class)` 方法向一个目标服务**贡献配置**。目标服务本身是一个普通的 IoC 服务，接收所有 Module 的贡献内容并合并。

### 6.2 ContributionDef 接口

```java
public interface ContributionDef {
    String getServiceId();
    boolean isOptional();  // @Optional
    
    void contribute(ModuleBuilderSource, ServiceResources, Configuration);
    void contribute(ModuleBuilderSource, ServiceResources, OrderedConfiguration);
    void contribute(ModuleBuilderSource, ServiceResources, MappedConfiguration);
}
```

### 6.3 三种配置类型

| 配置类型 | 贡献点示例 | 说明 |
|----------|-----------|------|
| `Configuration<T>`（无序） | 不常用 | 无序集合 |
| `OrderedConfiguration<T>` | `RouteRegistry`、`HttpFilterChain`、`ExceptionMapperChain` | 排序集合，支持 `@Order(before/after)` |
| `MappedConfiguration<K,V>` | `SymbolProvider`、`RowMapperOverrides` | 键值对映射 |

### 6.4 执行流程

`ContributionDefImpl.contribute()` 内部：
```
contribute(moduleSource, resources, configuration)
    → Map<Class<?>, Object> resourceMap：
        { parameterType → configuration,
          ServiceLocator.class → resources,
          Logger.class → resources.getLogger() }
    → 对每个非目标配置类型添加 WrongConfigurationTypeGuard
    → 静态方法无需实例，否则从 moduleSource.getModuleBuilder() 获取
    → InternalUtils.calculateParametersForMethod() → 参数注入
    → contributorMethod.invoke(moduleInstance, params...)
        → 用户代码向 config.add("id", value) 添加贡献
```

### 6.5 排序控制

`@Order(before="xxx", after="xxx")` 控制 `OrderedConfiguration` 中的插入位置。`@FactoryDefaults` 标记的贡献作为整个贡献链的默认值基座。

---

## 7. 关键内部实现一览

| 类 | 所在文件 | 职责 |
|----|---------|------|
| `RegistryImpl` | `freeway-ioc/.../internal/RegistryImpl.java` (1493行) | 组装 Module、管理 builtin 服务、生命周期编排 |
| `ModuleImpl` | `freeway-ioc/.../internal/ModuleImpl.java` (777行) | 服务代理缓存、advisor 匹配、contribution 查找、EagerLoad 收集 |
| `DefaultModuleDefinition` | `freeway-ioc/.../internal/DefaultModuleDefinition.java` (712行) | 扫描 module class 的方法 + 注解，生成所有 Def |
| `ServiceDefinitionImpl` | `freeway-ioc/.../internal/ServiceDefinitionImpl.java` | 封装服务 ID、接口、实现类、scope、markers、eagerLoad 等元数据 |
| `ServiceBinderImpl` | `freeway-ioc/.../internal/ServiceBinderImpl.java` (398行) | 将 `bind()` 调用转换为 `ServiceDefinition` |
| `ObjectInjectorImpl` | `freeway-ioc/.../internal/ObjectInjectorImpl.java` | 执行 ServiceProvider 责任链 |
| `AdvisorStackBuilder` | `freeway-ioc/.../internal/AdvisorStackBuilder.java` | 将 Advisor 栈包装为 interceptor 链 |
| `SingletonServiceLifecycle` | `freeway-ioc/.../internal/SingletonServiceLifecycle.java` | 单例：调用 creator.create() 一次 |
| `PerThreadServiceLifecycle` | `freeway-ioc/.../internal/PerThreadServiceLifecycle.java` | perthread 作用域，每个线程独立实例 |
| `ConstructorServiceCreator` | `freeway-ioc/.../internal/ConstructorServiceCreator.java` | 通过构造函数注入创建服务实例 |
| `OperationAdvisorImpl` | `freeway-ioc/.../internal/OperationAdvisorImpl.java` (132行) | `@Operation` 注解驱动的操作追踪 advice |
| `LazyAdvisorImpl` | `freeway-ioc/.../internal/LazyAdvisorImpl.java` | Thunk 延迟加载 advice |
| `LoggingAdvisorImpl` | `freeway-ioc/.../internal/LoggingAdvisorImpl.java` | 日志注入 advice |
| `FreewayIOCModule` | `freeway-ioc/.../ioc/FreewayIOCModule.java` (361行) | 内置的根 Module，注册 30+ 基础设施服务 |
| `DefaultServiceProxyBuilderImpl` | `freeway-ioc/.../internal/DefaultServiceProxyBuilderImpl.java` | 创建 no-op 代理实现 |
| `TypeCoercerImpl` | `freeway-ioc/.../internal/TypeCoercerImpl.java` | 字符串→类型转换链 |
| `SymbolSourceImpl` | `freeway-ioc/.../internal/SymbolSourceImpl.java` | `${...}` 占位符解析 |
| `ContributionDefImpl` | `freeway-ioc/.../internal/ContributionDefImpl.java` | `@Contribute` 方法的具体执行 |
| `ClassPathScannerImpl` | `freeway-ioc/.../internal/ClassPathScannerImpl.java` | 类路径扫描器，用于 SPI 发现 |
| `CronExpression` | `freeway-ioc/.../ioc/schedule/CronExpression.java` | Cron 表达式解析 |
| `PeriodicExecutorImpl` | `freeway-ioc/.../internal/cron/PeriodicExecutorImpl.java` | 定时任务调度器 |
| `UpdateListenerHubImpl` | `freeway-ioc/.../internal/UpdateListenerHubImpl.java` | 配置变更通知中枢 |
| `RegistryShutdownHubImpl` | `freeway-ioc/.../internal/RegistryShutdownHubImpl.java` | 优雅关闭管理 |

---

## 8. 线程模型与生命周期

### 8.1 启动时序

```
RegistryImpl 构造时：
    ├─ PerthreadManagerImpl → 管理 perthread scope
    ├─ ServiceActivityTrackerImpl → 跟踪服务定义/创建状态
    ├─ RegistryShutdownHubImpl → shutdown hook 管理器
    ├─ 所有 ModuleImpl 实例化 → 服务代理缓存就绪
    └─ 所有 builtin 服务注册

performRegistryStartup()：
    ├─ OneShotLock 保证一次性执行
    ├─ @EagerLoad 服务强制实例化
    ├─ @Startup 方法遍历执行（异常隔离，一个失败不影响其他）
    │   └─ 方法参数自动注入（可访问所有已就绪的服务）
    ├─ RegistryStartup Runnables 执行
    └─ cleanupThread() → 清理线程局部
```

### 8.2 关闭时序

```
RegistryShutdownHub.shutdown()
    → addRegistryShutdownListener 的 listeners 倒序执行
    → ParallelExecutor 线程池 shutdown
    → PerThreadServiceLifecycle 清理
    → PerthreadManager 线程局部清理
```

### 8.3 线程模型

| 组件 | 说明 |
|------|------|
| `ParallelExecutor` | 使用 `Executors.newVirtualThreadPerTaskExecutor()`（JDK 虚拟线程） |
| `NonParallelExecutor` | 当线程池禁用时使用（`THREAD_POOL_ENABLED=false`） |
| `PerthreadManager` | 每个请求结束时清理线程局部状态 |
| `ThunkCreator` | 创建延迟对象，隔离并发访问 |

---

## 9. IoC 架构全景图

```
                    Registry（门面）
                         │
             ┌───────────┼───────────┐
             │           │           │
      ServiceLocator  Registry.Builder  RegistryShutdownHub
             │
    ┌────────┴────────┐
    │                 │
  Module（多个）     FreewayIOCModule（内置）
    │                 │
    ├─ build*()   →  ServiceDefinition
    ├─ advise*()  →  AdvisorDefinition
    ├─ contribute*() → ContributionDef
    ├─ bind()     →  ServiceBinder → ServiceDefinition
    └─ @Startup   →  StartupDef
         │
         ▼
    ServiceDefinition.createServiceCreator()
         │
    ObjectCreator（构造函数 + 注入计划）
         │
    AdvisorStackBuilder（AOP 织入）
         │
    AspectInterceptorBuilder.build()
         │
    JDK 动态代理（暴露给外部使用）
```

设计的核心哲学：

1. **无反射黑魔法** — 服务注册、注入、拦截全部通过 Module 静态方法 + MethodHandle 完成，清晰可见、可调试
2. **JDK 能力优先** — Records、密封类、模式匹配、虚拟线程，无历史包袱
3. **组合式扩展** — `@Contribute` 贡献点机制让一切可插拔，无框架核心变更即可扩展
4. **零外部依赖** — 容器核心不依赖 Spring、Guice、CDI，仅有 `javax.inject`（JSR-330 规范）
