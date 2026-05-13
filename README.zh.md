[English](README.md) | 中文

# Freeway Application Framework

**独立的 IoC & AOP + Boot + Web + DB — 纯 Java，组合优先。**

Freeway 是一个面向 **JDK 25+** 的现代轻量级 Java 应用框架，其核心理念只有一条：

> **最大限度地发挥 JDK 自身的能力。**

| 模块 | 说明 |
|------|------|
| `freeway-annotations` | IoC 注解（`@Contribute`, `@Startup`, `@Symbol` 等） |
| `freeway-commons` | JSON 解析器/生成器、日志适配器、类型转换 |
| `freeway-ioc` | IoC 容器核心（Module-Services 架构、SPI 扩展、类型转换、资源工具） |
| `freeway-db` | 数据库访问层 — 内置连接池、命名参数、Record/Bean 自动映射、Migration |
| `freeway-web` | 纯 Handler 路由的 Web 层（`FreewayContext`、`RouteRegistry`、异常映射、JDK HttpServer + robaho httpserver、虚拟线程） |
| `freeway-boot` | 启动器：配置加载（YAML、properties、环境变量、命令行）+ 启动生命周期 |
| `freeway-boot-starter` | POM 聚合器（便捷依赖） |

---

## 设计哲学

1. **精锐小团队（3–5 人）构建精锐应用** — 面向 CLI / Web 应用开发。
2. **不追求"大而全"** — 没有不必要的抽象，没有臃肿的配置。
3. **JDK 能力优先** — 充分利用 JDK 的特性（records、sealed types、pattern matching、虚拟线程）。零历史兼容包袱。
4. **外部依赖是最后的选择** — 每个依赖都必须证明自己存在的价值。
5. **不依赖反射黑魔法** — 服务注册通过 Module 类上的静态方法完成，清晰可见、可调试。

---

## 快速开始

### Maven

```xml
<dependency>
    <groupId>com.jujin</groupId>
    <artifactId>freeway-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 你的第一个 Freeway 应用

```java
@FreewayBootEntry
public class MyApp {

    public static void bind(ServiceBinder binder) {
        binder.bind(Greeter.class, GreeterImpl.class);
    }

    public static void main(String[] args) {
        FreewayApp app = FreewayApplication.run(MyApp.class, args);
    }
}
```

### 带 Web 能力

```java
@FreewayBootEntry
public class WebApp {

    public static void bind(ServiceBinder binder) {
        binder.bind(Greeter.class, GreeterImpl.class);
    }

    @Contribute(RouteRegistry.class)
    public static void routes(OrderedConfiguration<RouteDef> config) {
        config.add("hello", new RouteDef("GET", "/api/hello", ctx ->
            ctx.send(200, "Hello, Freeway!")));
        config.add("greet", new RouteDef("GET", "/api/greet/{name}", ctx ->
            ctx.send(200, "Hello, " + ctx.pathVar("name") + "!")));
    }

    @Contribute(HttpFilterChain.class)
    public static void filters(OrderedConfiguration<HttpFilter> config) {
        config.add("log", (ctx, chain) -> {
            System.out.println(ctx.method() + " " + ctx.path());
            chain.proceed(ctx);
        });
    }

    public static void main(String[] args) {
        FreewayApp app = FreewayApplication.run(WebApp.class, args);
    }
}
```

### JSON（内建于 `freeway-commons`）

```java
// 序列化
String json = JSONUtils.toJson(Map.of("name", "Freeway", "version", 1.0));
// → {"name":"Freeway","version":1.0}

// 解析
JSONObject obj = (JSONObject) JSONUtils.fromJson(json);
String name = obj.getString("name");  // "Freeway"

// 类型化反序列化
MyBean bean = JSONUtils.fromJson(json, MyBean.class);
```

---

## 架构

### 模块结构

```
freeway-annotations    →  @Startup, @Contribute, @Inject, @Autobuild, @Symbol, @Value 等
freeway-commons        →  JSON（零依赖解析器/生成器）、日志适配器、类型转换
freeway-ioc            →  Module-Services 架构、SPI 扩展、强大的 IoC & AOP 能力
freeway-db             →  DbModule — 内置连接池、命名参数、Record/Bean 映射、Migration
freeway-web            →  WebModule — 纯 Handler 路由、JDK HttpServer + robaho httpserver、虚拟线程
freeway-boot           →  FreewayApplication — 配置加载 + 启动
freeway-boot-starter   →  POM 聚合器
```

### 核心概念 — Module

Freeway IoC 以 **Module** 为基本单元组织整个容器。Module 是一个普通的 Java 类，通过**方法命名约定**或**注解**（二者都是框架的一等公民，地位完全等同）来声明服务、增强和贡献：

```java
// 纯命名约定
buildGreeter()             → 服务定义
adviseGreeter()            → 服务增强
contributeToGreeter()      → 服务贡献
startup()                  → 生命周期回调

// 注解驱动（等价的写法）
@Build  Greeter buildGreeter()
@Advise(serviceInterface = Greeter.class) void adviseGreeter(...)
@Contribute(Greeter.class) void contributeToGreeter(...)
@Startup void onStart()
```

Module 通过 `ModuleProvider` SPI（JDK `ServiceLoader`）注册，并通过 `@ImportModule` 形成显式的组合树。

---

### IoC 容器 — 五大核心能力

#### 1. 服务定义 — `build`

`build*` 方法声明一个服务，包含唯一的**服务 ID**、**接口**和**作用域**：

| 元素 | 说明 |
|------|------|
| `ServiceDef` | 服务描述符（ID、接口、作用域、标记） |
| `ServiceBinder` | 声明式绑定（Guice 风格的 `bind(A, AImpl)`） |
| `@Scope` | 生命周期：`"singleton"`（默认）、`"perthread"`、自定义 |
| `@EagerLoad` | 启动时立即实例化，而非懒加载 |
| `@Primary` | 多个实现中标记为首选 |
| `@Marker` | 基于注解的类型安全限定符 |
| `ServiceLifecycle` | 自定义作用域实现 |

```java
// 三种定义服务的方式：
public Greeter buildGreeter() { return new GreeterImpl(); }

public void bind(ServiceBinder binder) {
    binder.bind(Greeter.class, GreeterImpl.class);
}

// 通过 Registry.Builder
registry.addService(Greeter.class, ctx -> new GreeterImpl());
```

#### 2. 服务增强 — `advise`（方法级拦截）

`advise*` 方法（或 `@Advise`）声明了对目标服务的方法级拦截器。这是 Freeway IoC 统一的 AOP 管道——没有 `@Aspect`、没有 `@Around/@Before/@After`、没有切点表达式：

```
@Advise 注解的方法
  → AdvisorDef（定义：匹配哪些服务）
    → ServiceAdvisor.advise(MethodAdviceReceiver)
      → receiver.adviseMethod("methodName", MethodAdvice)
        → MethodAdvice.advise(MethodInvocation)  ◄── 单一回调
```

```java
public class MyModule {

    @Advise(serviceInterface = Greeter.class)
    public void upperCaseName(ServiceAdvisor advisor) {
        advisor.adviseMethod("greet", invocation -> {
            Object[] args = invocation.args();
            if (args.length > 0 && args[0] instanceof String name) {
                args[0] = name.toUpperCase();
            }
            return invocation.proceed();
        });
        advisor.adviseMethod("farewell", /* 同样的模式 */);
    }
}
```

关键元素：

| 元素 | 用途 |
|------|------|
| `@Advise(serviceInterface)` | 标记一个增强方法，绑定到指定服务类型 |
| `AdvisorDef` | 定义：`matches(ServiceDef)` 过滤器 |
| `ServiceAdvisor` | 增强执行者：在 `MethodAdviceReceiver` 上注册 Advice |
| `MethodAdvice` | `@FunctionalInterface` — 包含 `proceed()` 的单一回调 |
| `MethodAdviceReceiver` | 按**方法名**注册 Advice |
| `@Order` | 控制多个 Advisor 的执行顺序 |
| `@PreventServiceDecoration` | 抑制代理生成（取消增强） |

多个 Advisor 叠加形成拦截器链：

```
serviceProxy.method()
  → Advisor A（外层）
    → Advisor B（内层）
      → 原始 service.method()
```

没有 `@Decorate`、没有 `@Match`、没有双路径混淆——只有一条精确的 `@Advise` → `MethodAdvice` 管道。

#### 3. 服务贡献 — `contribute`（插件扩展）

`contribute*` 方法（或 `@Contribute`）向**另一个服务配置**中注入数据。这是 Freeway 最具特色的**插件式扩展机制**：

```java
// 目标声明它接受钩子注入
@UsesConfiguration(Interceptor.class)
public interface GreeterInterceptorSource { }

// 任何位置的 Module 都可以贡献
@Contribute(GreeterInterceptorSource.class)
public static void contributeToGreeter(Configuration<Interceptor> config) {
    config.add(new LoggingInterceptor());
    config.add(new MetricsInterceptor());
}
```

三种配置形态：

| 形态 | 接口 | 行为 |
|------|------|------|
| 无序 | `Configuration<T>` | 仅添加，无顺序 |
| 有序 | `OrderedConfiguration<T>` | 每项有唯一 ID + 相对定位（`before/after`） |
| 键值对 | `MappedConfiguration<K,V>` | 唯一键映射，重复键报错 |

相关注解：

| 注解 | 用途 |
|------|------|
| `@UsesConfiguration` | 声明无序配置槽 |
| `@UsesOrderedConfiguration` | 声明有序配置槽 |
| `@UsesMappedConfiguration` | 声明键值对配置槽 |
| `@Optional` | 贡献是可选的；目标服务不存在时跳过 |

#### 4. 自动装配 — `@Inject`

Freeway IoC 提供了统一的注入引擎，在**三个插入点**运作：

| 插入点 | 方式 |
|--------|------|
| **构造器参数** | 容器选择一个构造器，解析每个参数 |
| **字段注入** | 构造完成后，填充 `@Inject` 标记的字段 |
| **Module 方法参数** | `build*()`、`advise*()`、`contribute*()` 的方法参数自动注入 |

---

##### `@Inject` 注解（双源设计）

Freeway 自带 `@Inject`（位于 `freeway-annotations`），与 JSR-330 的 `javax.inject.Inject` **共存**。注入引擎对二者一视同仁，只有一个额外增强：

```java
// com.jujin.freeway.ioc.annotations.Inject
@Target({ FIELD, PARAMETER, CONSTRUCTOR })
@Retention(RUNTIME)
public @interface Inject {
    /** 按服务 ID 注入。留空则按类型注入。 */
    String value() default "";
}
```

| 注解 | 行为 |
|------|------|
| `@Inject`（无 value） | **类型注入**——容器查找与字段/参数类型匹配的单一服务 |
| `@Inject("serviceId")` | **按名注入**——直接通过服务 ID 解析（无需 `@Named`） |
| `@Inject` + `@Named("id")` | **按名注入（JSR-330）**——`javax.inject.Named` 同样生效 |
| `javax.inject.Inject` | **类型注入**——行为一致，JSR-330 兼容 |

> **为什么需要一个单独的 `@Inject`？** Freeway 的 `@Inject` 上的 `value()` 属性消除了常见场景下对额外 `@Named` 注解的需求。写 `@Inject("db")` 一个注解实现了两个注解的功能。

---

##### 构造器选择优先级

容器实例化服务时，按下述优先级选择构造器：

1. 带有 `javax.inject.Inject` 注解的构造器
2. 带有 `com.jujin.freeway.ioc.annotations.Inject` 注解的构造器
3. **参数最多的构造器**（自动检测，无需注解）
4. 默认（无参）构造器作为兜底

```java
public class MyService {
    // 优先级 1 或 2：显式 @Inject
    @Inject
    public MyService(DependencyA a, DependencyB b) { ... }

    // 优先级 3：参数最多的胜出（如果没有任何构造器带 @Inject）
    public MyService(DependencyA a) { ... }
    public MyService() { ... }  // 输给上面那个
}
```

---

##### 注入解析链

每一次注入请求都会经过 `ObjectInjector` —— 一个责任链模式，按顺序咨询 `InjectResolver` 实例。第一个非空结果胜出：

```
InjectResolver[0]  →  InjectResolver[1]  →  ...  →  兜底: locator.getService()
```

默认解析器处理以下场景：

1. **`@Value("${symbol.key:default}")`** — 从配置中解析，转换到目标类型（支持默认值）
2. **`@Symbol("KEY")`** — 从符号源解析（如环境变量、系统属性）
3. **`@Autobuild`** — 自动实例化该类并注入其依赖
4. **`ServiceOverride`** — 编程方式注册，在常规查找之前检查
5. **兜底** — `ServiceLocator.getService(type)`（当 `required=true`）

```java
// 每种解析策略的示例
public class DemoService {

    @Inject  @Value("${app.timeout:5000}")
    private int timeout;  // 从配置解析，String 转换为 int

    @Inject  @Symbol("DATABASE_URL")
    private String dbUrl; // 从符号源解析

    @Inject  @Autobuild
    private ComplexHelper helper;  // 由容器实例化 + 注入

    @Inject
    private SimpleService svc;     // 类型注入：通过 ServiceLocator 查找

    @Inject("specificService")
    private NamedService named;    // 按服务 ID 的命名注入
}
```

---

##### 字段注入机制

字段注入在**构造完成之后**执行。引擎遍历整个类继承层次（直到 `Object`），处理所有被 `javax.inject.Inject` 或 Freeway 的 `@Inject` 标记的**非 static、非 final** 字段：

```
对于每个字段：
    1. 如果 Freeway 的 @Inject 有非空 value() → locator.getService(value, fieldType)
    2. 如果 javax.inject.Named 存在            → locator.getService(named.value, fieldType)
    3. 否则 → 类型注入：查找资源或 locator.getObject(fieldType)
```

---

##### 注入后回调

所有字段注入完成后，容器调用被 `@PostInjection` 或 `javax.annotation.PostConstruct` 标记的方法：

```java
public class MyService {
    @Inject private Dependency dep;

    @PostInjection
    public void init() {
        // 此时 dep 保证已被注入
        dep.register(this);
    }
}
```

与 JSR-250 的 `@PostConstruct` 不同，Freeway 的 `@PostInjection` 允许**多个方法**和**方法参数**（参数也会被注入）。

---

##### Module 方法参数注入

`build*()`、`advise*()` 和 `contribute*()` 方法的参数同样会被注入——这是 Freeway Module 获取依赖而不需要显式调用 `getService()` 的方式：

```java
public class MyModule {
    // 'storage' 按类型注入
    public void buildGreeter(Storage storage) {
        return new GreeterImpl(storage);
    }

    // 'config' 是由容器注入的 OrderedConfiguration
    @Contribute(RouteRegistry.class)
    public static void addRoutes(OrderedConfiguration<RouteDef> config) {
        config.add("route", new RouteDef(...));
    }

    // '@Value' 同样可以应用于 Module 方法参数
    public void buildDataSource(@Value("${db.url}") String url) {
        return new DataSource(url);
    }
}
```

---

##### 测试中的 ServiceOverride

`ServiceOverride` 挂入注入链：当一个类型被覆盖时，容器中**所有**注入点都会自动接收到替换后的实现：

```java
Registry registry = Registry.builder()
    .addModule(ProdModule.class)
    .addServiceOverride(Database.class, ctx -> new InMemoryDatabase())
    .build();
```

大多数场景下不需要 Mock 库。覆盖在每次服务查找之前检查，无论是 `@Inject` 字段还是构造器/参数解析。

---

##### 基础设施概览

| 组件 | 角色 |
|------|------|
| `ObjectInjector` | 责任链：迭代 `InjectResolver`，兜底用 `getService()` |
| `InjectResolver` | 自定义解析策略的 SPI |
| `ServiceOverride` | 类型覆盖，在链的最前端检查 |
| `GenericsResolver` | 解析泛型类型参数（如 `List<String>`） |
| `PropertyAdapter` | Bean 属性访问，用于注入到 JavaBean 风格的 setter 方法 |

##### ServiceId — 按 ID 解析多实现

当同一类型下注册了多个实现时，你需要告诉容器要哪一个。Freeway 支持两种方式：**Marker 注解**（前面已介绍）和 **ServiceId**——一种轻量级的字符串标识符。

###### 1. 注册时指定 ServiceId

**方式 A — 绑定链中指定（推荐）：**

```java
public static void bind(ServiceBinder binder) {
    binder.bind(MultiSvc.class, FastMultiSvcImpl.class).withId("fast");
    binder.bind(MultiSvc.class, SlowMultiSvcImpl.class).withId("slow");
}
```

`withId(id)` 设置了服务在注册表中的标识。如果省略，默认的 ServiceId 是服务接口的简单类名（例如 `"MultiSvc"`）。

**方式 B — 在实现类上用 `@ServiceId` 注解：**

```java
@ServiceId("fast")
public class FastMultiSvcImpl implements MultiSvc { ... }

@ServiceId("slow")
public class SlowMultiSvcImpl implements MultiSvc { ... }
```

当服务类被发现时（例如通过 `@Autobuild` 或 SPI 扫描），注解会被读取。它优先于默认的基于类型的 ServiceId。

---

###### 2. 按 ServiceId 注入

**方式 A — `@Inject("id")`（最常用）：**

```java
public class Consumer {
    @Inject("fast")
    private MultiSvc svc;  // 选择 FastMultiSvcImpl

    @Inject("slow")
    private MultiSvc anotherSvc;  // 选择 SlowMultiSvcImpl
}
```

Freeway 的 `@Inject` 的 `value()` 属性同时兼任 ServiceId 过滤器。这等价于 `@Inject @Named("fast")`，但少了一个注解导入。

**方式 B — 编程式查找：**

```java
MultiSvc svc = registry.getService("fast", MultiSvc.class);
```

---

###### 3. 解析逻辑

当遇到 `@Inject("fast")` 时：

1. 容器检查是否有已注册的服务同时匹配类型（`MultiSvc`）**和** ServiceId（`"fast"`）。
2. 如果找到，直接返回该实现。
3. 如果未找到，抛出 `IllegalStateException`——不会降级到其他 ServiceId。

这与基于类型的降级不同：**使用 ServiceId 时，匹配在两个维度上都是精确的。**

---

###### 4. ServiceId 与 Marker 对比——什么时候用哪个

| 维度 | ServiceId | Marker 注解 |
|------|-----------|-------------|
| 声明方式 | `withId("x")` 或 `@ServiceId("x")` | `@Marker(Red.class)` 或 `@Red` |
| 注入语法 | `@Inject("x") MultiSvc svc` | `@Inject @Red MultiSvc svc` |
| 是否需要额外类型 | 否（纯字符串） | 是（需要定义 `@interface`） |
| 类型安全 | 运行时（字符串可能拼错） | 编译期（编译器验证注解） |
| 可发现性 | 需要文档或约定 | 注解直接出现在注入点 |
| 重构友好度 | 字符串重命名不会标记调用方 | IDE 重命名重构原生支持 |
| 最适合场景 | 快速区分、原型开发或小型项目 | 架构约束、跨模块契约或团队级约定 |

**经验法则：** 当你只需要区分两个东西时用 ServiceId。当你想编码具有业务含义、应该在重构中保持不变的语义时用 Marker。

---

##### @Inject 速查表

| 场景 | 使用方式 | 示例 |
|------|---------|------|
| 单一实现，按类型注入 | `@Inject`（不加 value） | `@Inject Greeter greeter;` |
| 多实现，知道服务 ID | `@Inject("id")` | `@Inject("fast") Cache cache;` |
| 多实现，有 Marker 注解 | `@Inject` + `@Marker` | `@Inject @Fast Cache cache;` |
| 配置值带默认值 | `@Inject @Value("${key:default}")` | `@Inject @Value("${port:8080}") int port;` |
| 环境变量 / 符号值 | `@Inject @Symbol("KEY")` | `@Inject @Symbol("HOME") String home;` |
| 由容器管理实例化 | `@Inject @Autobuild` | `@Inject @Autobuild Helper h;` |
| 不可变服务（推荐） | 构造器 `@Inject` | `@Inject public MyService(Dep d) { ... }` |
| 依赖就绪后初始化 | `@PostInjection` | `void init() { ... }` |
| 测试——容器层面替换 | `ServiceOverride` | `.addServiceOverride(Db.class, ...)` |

##### `@Inject` 解析失败时

注入链设计为快速失败、清晰报错：

| 现象 | 可能原因 | 修复方法 |
|------|---------|---------|
| `IllegalStateException: No service found for type X` | 服务未在任何 Module 中注册 | 添加 `buildX()` 方法，或检查 `ModuleProvider` SPI |
| `AmbiguousServiceException` | 多个服务匹配同一类型 | 添加 `@Inject("id")` 或 `@Marker` 注解来消除歧义 |
| `TypeCoercionException` | `@Value("${...}")` 的字符串无法转换为目标类型 | 添加自定义 `TypeCoercer`，或将字段类型改为 `String` |
| 注入后字段仍为 `null` | 字段是 `static` 或 `final` | 去掉 `static`/`final`，或改用构造器注入 |

##### 选择注入策略的决策树

```
需要不可变性（final 字段）吗？
  └─ 是 → 构造器注入（@Inject 标注构造器）
  └─ 否 → 依赖是否总是可用？
             └─ 是 → 字段注入（@Inject）
             └─ 否 → 是否是配置值？
                        └─ 是 → @Value("${key:default}") 或 @Symbol("KEY")
                        └─ 否 → 是否需要容器实例化？
                                   └─ 是 → @Autobuild
                                   └─ 否 → 通过 ServiceLocator 编程式查找
```

#### 5. 生命周期管理 — `@Startup`

@Startup` 是 Freeway Module 生命周期中"万事俱备，请开始行动"的最终信号, 这里有关于'@Startup'的[说明和指南](STARTUP-GUIDE.zh.md)

**作用域：**

| 作用域 | 行为 |
|--------|------|
| `singleton`（默认） | 懒加载实例化，全局缓存 |
| `perthread` | 每个线程一个实例 |
| 自定义 | 通过 `ServiceLifecycle` 接口 |

**启动与关闭：**

| 机制 | 触发时机 |
|------|---------|
| `@Startup` | `Registry.performRegistryStartup()` 触发 |
| `@EagerLoad` | 强制在启动时实例化 |
| `RegistryShutdownListener` | 优雅关闭回调 |
| `ThreadCleanupListener` | 线程级清理 |

**服务创建管道（`ModuleImpl` 内部）：**

```
ServiceBuilder → ObjectCreator
  → LifecycleWrappedServiceCreator      （作用域包装）
  → AdvisorStackBuilder                 （增强编织）
  → RecursiveServiceCreationCheckWrapper（循环检测）
  → OperationTrackingObjectCreator      （操作追踪）
```

---

### 概念关系图

```
┌───────────────────────────────────────────────────────────────┐
│                        Registry.Builder                       │
│                    （注册 Module → 构建 Registry）              │
└──────────┬────────────────────────────────────────────────────┘
           │
           ▼
    ┌───────────────────────┐
    │     Module            │  ← @ImportModule 形成组合树
    │   （组织单元）           │  ← ModuleProvider SPI 自动发现
    └────┬──────┬───────┬───┘
         │      │       │
    build│   advice     │contribute
         │      │       │
    ┌────┴──┐ ┌─┴─────┐┌┴──────────────┐
    │Service│ │Advisor││ Configuration │
    │  Def  │ │  Def  ││（无序/有序/映射）│
    │  +    │ │  →    │└───────────────┘
    │Scope  │ │Method │
    │Lifecy.│ │Advice │
    └───┬───┘ └───┬───┘
        │         │
        └────┬────┘
             ▼
     ┌───────────────┐
     │    自动注入     │
     │ ( @Inject 等)  │
     └───────────────┘
```

---

## 快速示例

### Module 使用 SPI 发现

```java
public class MyModule {

    public static void bind(ServiceBinder binder) {
        binder.bind(Service1.class, ServiceImpl1.class);
    }

    @Contribute(RouteRegistry.class)
    public static void addRoutes(OrderedConfiguration<RouteDef> config) {
        config.add("route1", new RouteDef("GET", "/path", handler));
    }

    public Service2 buildService(Service1 service1) {
        return new Service2Impl(service1);
    }

    @Startup
    public static void onStart(Registry registry) {
        // 在所有服务就绪后执行
    }
}
```

```
# META-INF/services/com.jujin.freeway.ioc.ModuleProvider
com.example.MyModuleProvider
```

```java
public class MyModuleProvider implements ModuleProvider {
    public Class<?>[] modules() {
        return new Class<?>[] { MyModule.class };
    }
}
```

### 配置注入

```java
public class MyService {
    @Symbol("app.server.port")
    private int port;

    @Value("${app.name:Freeway}")
    private String appName;
}
```

配置源（优先级从高到低）：
1. 命令行参数（`--key=value`）
2. `application.yml` / `application.yaml`（classpath）
3. `application.properties`（classpath）
4. 环境变量（前缀 `FREEWAY_`）

---

## 经典用例

### 1. 三层架构（Module 作为组织者）

Module 天然对应应用的**垂直切片**——它在同一处声明服务、增强器和贡献点。没有 XML，没有散落在各包中的 `@Component`/`@Service`：

```java
public class GreeterModule {

    // ── 服务层 ──
    @Build
    Greeter buildGreeter(Storage storage) {
        return new GreeterImpl(storage);
    }

    // ── 横切关注点：日志（方法级）──
    @Advise(serviceInterface = Greeter.class)
    public void logGreeting(ServiceAdvisor advisor) {
        advisor.adviseAll(invocation -> {
            System.out.println(">> " + invocation.method().getName());
            long t = System.nanoTime();
            Object result = invocation.proceed();
            System.out.println("<< " + (System.nanoTime() - t) / 1_000_000 + "ms");
            return result;
        });
    }

    // ── 插件扩展点：让其他人添加拦截器 ──
    @UsesOrderedConfiguration(Interceptor.class)
    public interface GreeterHooks { }

    // ── 生命周期 ──
    @Startup
    void onStart(Registry registry) {
        System.out.println("GreeterModule ready");
    }
}

public class StorageModule {
    @Build
    Storage buildStorage() {
        return new InMemoryStorage();
    }
}
```

**为什么这很重要：** 一个功能的所有关注点（服务 + 拦截器 + 扩展点 + 生命周期）都集中在一个 Module 类中。增加一个新功能就是增加一个新 Module——而不是修改五个不同的包。

---

### 2. 事务管理 via `@Advise`

没有 `@Transactional` 注解魔法。一个 Advisor 就能用事务生命周期包裹所有 repository 方法：

```java
public class TransactionModule {

    @Advise(serviceInterface = Repository.class)
    public void manageTransactions(ServiceAdvisor advisor, DataSource ds) {
        advisor.adviseMethod("save", invocation -> {
            try (var conn = ds.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    invocation.proceed();
                    conn.commit();
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                }
            }
        });
        // 同样的模式用于 update()、delete()...
    }
}
```

因为 `@Advise` 针对的是**服务接口**并按**方法名**映射，你可以获得精确的控制：
- `adviseAll(...)` — 包裹服务上的每个方法
- `adviseMethod("save", ...)` — 只包裹 `save()`
- 同一服务上的多个 Advisor = 可组合的管道

---

### 3. 插件架构 via `@Contribute`

`@Contribute` 机制使任何服务都可以**从外部扩展**——这是插件系统的定义性特征：

```java
// ── 框架声明一个钩子点 ──
@UsesOrderedConfiguration(Filter.class)
public interface FilterChain { }

// ── 插件 A（在它自己的 JAR 中）──
public class LoggingPlugin {
    @Contribute(FilterChain.class)
    public static void addLogging(OrderedConfiguration<Filter> config) {
        config.add("log", ctx -> {
            System.out.println("request: " + ctx.path());
            return ctx.proceed();
        });
    }
}

// ── 插件 B（另一个 JAR）──
public class MetricsPlugin {
    @Contribute(FilterChain.class)
    public static void addMetrics(OrderedConfiguration<Filter> config) {
        config.add("metrics", ctx -> {
            long t = System.nanoTime();
            var result = ctx.proceed();
            recordLatency(System.nanoTime() - t);
            return result;
        }, "before:log"); // ← 位置约束
    }
}
```

**与 Spring 的关键区别：** Contribution 是**静态的、类型检查的、可发现的**——没有运行时组件扫描，没有反射。`OrderedConfiguration` 支持 `before`/`after` 定位，插件可以声明排序而无需脆弱的数字优先级。

---

### 4. 测试——通过 `ServiceOverride` 替换服务

测试不需要特殊的测试框架。Freeway 的 `ServiceOverride` 让任何测试 Module 可以按**类型**替换生产服务：

```java
// ── 生产模块 ──
public class ProdModule {
    @Build
    Database buildDatabase() {
        return new PostgresDatabase("prod-url");
    }
}

// ── 测试模块 ──
public class TestModule {
    @Build
    Database buildDatabase() {
        return new InMemoryDatabase();
    }
}

// ── 测试 ──
class MyServiceTest {
    Registry registry;

    @BeforeEach
    void setUp() {
        registry = Registry.builder()
            .addModule(ProdModule.class)
            .addServiceOverride(Database.class, ctx -> new InMemoryDatabase()) // 内联覆盖
            .build();
    }

    @Test
    void testBusinessLogic() {
        var service = registry.getService(MyService.class);
        assertDoesNotThrow(() -> service.doWork());
    }
}
```

大多数场景不需要 Mock 库。`ServiceOverride` 在容器级别工作——所有模块中所有注入点自动接收测试替换。

---

### 5. 多模块组合 via `@ImportModule`

大型应用将 Module 组合成树。父 Module **显式导入**子模块——没有隐藏的自动发现：

```java
@ImportModule({
    UserModule.class,
    OrderModule.class,
    PaymentModule.class
})
public class AppModule {
    // AppModule 聚合所有功能模块
}

// ── 每个功能模块都可以独立测试 ──
public class UserModule {
    @Build
    UserRepository buildUserRepo() { return new UserJdbcRepo(); }

    @Contribute(RouteRegistry.class)
    public static void userRoutes(OrderedConfiguration<RouteDef> routes) {
        routes.add("user.create", new RouteDef("POST", "/users", ...));
        routes.add("user.list",   new RouteDef("GET",  "/users", ...));
    }
}
```

组合树是**可见且确定的**：
- 没有随机的 classpath 扫描
- 父模块控制哪些功能被装配在一起
- 子模块不需要知道谁导入了它们
- 不同的部署可以从相同的库模块组装不同的组合树

---

### 6. Marker 注解——区分同一接口的多实现

当多个服务实现同一个接口时，`@Marker` 注解以**类型安全**的方式解析注入——没有魔法字符串：

```java
// ── 定义 Marker 注解 ──
@Marker
@Target(TYPE)
@Retention(RUNTIME)
public @interface Fast { }

@Marker
@Target(TYPE)
@Retention(RUNTIME)
public @interface Reliable { }

// ── 应用到服务 ──
@Fast
@Build
Cache buildFastCache() { return new LocalCache(); }

@Reliable
@Build
Cache buildReliableCache() { return new RedisCache(); }

// ── 按 Marker 注入 ──
public class ReportService {
    @Inject @Fast
    Cache cache;   // << 明确的：快速的那个

    @Inject @Reliable
    Cache auditCache;  // << 明确的：可靠的那个
}
```

#### @Marker 作为元能力

`@Marker` 不仅仅是 `ServiceId` 的替代方案——它是一种**元能力**，将 Freeway 的匹配引擎开放给用户自定义注解，使它们成为框架的一等公民。

**为什么这很重要——四个视角：**

**1. 注解即契约**

用户自定义的注解（如 `@Fast`、`@Audited`、`@PrimaryDC`）参与与框架内置注解相同的匹配逻辑（`findServiceDefsMatchingMarkerAndType()`）。无需 SPI、无需接口注册——只需一个 `@interface`。框架将你的注解视为原生概念。

**2. 跨层级传播与组合**

Marker 形成层级继承链：

```
模块级 @Marker(Builtin.class)     → 该模块所有服务的默认标记
         ↓ 继承
服务级 @Marker(Red.class)          → 叠加在模块默认标记之上
         ↓
注入点 @Inject @Red @Fast Service → containsAll 匹配叠加的所有标记
```

`@Contribute` 和 `@Advise` 同样受 Marker 约束——一个 `@Contribute @Fast void contribute(...)` 只作用于标记了 `@Fast` 的服务。这是**基于注解作用域的声明式横切**，而非切点表达式。

**3. ServiceId 与 @Marker——能力层次**

| 维度 | ServiceId（工具能力） | @Marker（元能力） |
|------|---------------------|------------------|
| 本质 | 命名空间内的字符串标识符 | 可扩展的类型系统机制 |
| 可扩展性 | 固定的（只是字符串） | 用户定义任意 `@interface`，即成框架原生 |
| 参与范围 | 仅服务注入 | 注入解析 + 贡献匹配 + 增强作用域 + 模块定义 |
| 组合性 | 无（单一 ID 精确匹配） | 多标记组合（`containsAll` 语义） |
| 领域语义 | 无（纯字符串） | 丰富的语义（`@ReadOnly`、`@PrimaryDC`、`@Infra`） |
| 重构 | 字符串重命名无声无息 | IDE 原生注解重命名 |

**4. @Marker 为架构解锁的能力**

| 层次 | 效果 | 示例 |
|------|------|------|
| 跨模块隔离 | 模块级 Marker 限制服务可见范围 | `@Marker(Internal.class) public class InfraModule {}` |
| 自定义注入维度 | 用户定义业务相关的筛选轴 | `@Inject @PrimaryDC DataSource ds` |
| 有作用域的横切 | 贡献/增强按标记自动过滤 | `@Contribute @Fast void config(ServiceBundle b)` |
| 架构级契约 | 团队定义的注解成为可执行的约束 | `@ReadOnly @Audited` 只匹配只读审计服务 |
| 声明式组合 | 多标记叠加，精确匹配 | `@Inject @Red @Audited Service svc` |

**经验法则：** `ServiceId` 是同一接口多实现的"快车道"；`@Marker` 是**扩展性杠杆**——当你希望自己的注解成为 Freeway 解析语义的一部分时使用它。

---

### 7. 多租户 perThread 作用域

`perthread` 作用域为每个线程创建隔离的服务实例——非常适合多租户或请求级状态，无需容器管理请求生命周期：

```java
@Scope("perthread")
@Build
CurrentUser buildCurrentUser() {
    return new CurrentUser();
}

// ── 每个线程获得自己的实例 ──
class RequestHandler {
    @Inject
    CurrentUser currentUser;

    void handle(String userId) {
        currentUser.setId(userId);
        // ... 所有下游代码看到的是这个线程的用户
    }
}
```

在 `@Startup` 阶段，你可以预先配置：

```java
@Startup
void warmup(Registry registry) {
    registry.parallelExecute(() -> {
        // 为工作线程预加载 per-thread 服务
    });
}
```

---

## 设计总结

| 模式 | Freeway 的方式 | vs. Spring |
|------|---------------|------------|
| 服务定义 | Module 中的静态 `build*()` 方法 | `@Component` classpath 扫描 |
| AOP | `@Advise` + `advisor.adviseMethod(...)` | `@Aspect` + 切点表达式 |
| 扩展点 | `@Contribute` 到 `OrderedConfiguration` | `@Autowired List<X>` + `@Order` |
| 模块组合 | 显式的 `@ImportModule` 树 | `@Import` + 自动配置 |
| 测试隔离 | 容器级别的 `ServiceOverride` | `@MockBean` / `@TestConfiguration` |
| 限定符 | 类型安全的 `@Marker` 注解 | `@Qualifier("string")` |
| 作用域 | 通过 `ServiceLifecycle` 接口自定义 | `@Scope("prototype")` 等 |

---

### Web——纯 Handler 架构

纯 Handler 模式——没有 `@Controller`、没有 `@RequestMapping`、没有基于反射的路由：

```java
@Contribute(RouteRegistry.class)
public static void routes(OrderedConfiguration<RouteDef> config) {
    config.add("hello", new RouteDef("GET", "/hello", ctx ->
        ctx.send(200, "World")));
    config.add("user", new RouteDef("GET", "/users/{id}", ctx ->
        ctx.send(200, "User: " + ctx.pathVar("id"))));
    config.add("json", new RouteDef("GET", "/data", ctx ->
        ctx.sendJson(200, Map.of("key", "value"))));
}
```

`HttpContext` 是与服务器无关的——改变一个 Maven 依赖即可切换 HTTP 引擎。

### JSON

零依赖的 JSON 库，覆盖 95% 的使用场景：

```java
JSONObject obj = new JSONObject("{\"name\":\"freeway\",\"tags\":[\"java\",\"ioc\"]}");
String name = obj.getString("name");        // "freeway"
JSONArray tags = obj.getJSONArray("tags");  // ["java","ioc"]

JSONObject data = new JSONObject()
    .put("project", "freeway")
    .put("version", 1.0)
    .put("active", true);
```

---

### DB — 数据库访问层

零外部依赖的数据库访问，内置连接池。`Database` 对象既是数据库句柄也是连接池——不需要独立的 `DataSource` + `HikariCP` + `JdbcTemplate`。

**快速开始：**

```java
// 独立使用（不依赖 IoC 容器）
Database db = Database.builder()
    .url("jdbc:postgresql://localhost:5432/mydb")
    .username("app").password("secret")
    .build();

// 查询 — 自动映射到 Record
record User(long id, String name, String email) {}

List<User> users = db.sql("SELECT id, name, email FROM users WHERE status = ?", "active")
    .list(User.class);

// 命名参数 — #name 风格（推荐），顺序无关
db.sql("INSERT INTO users (id, name, email) VALUES (#id, #name, #email)")
  .param("email", "alice@example.com")
  .param("name", "Alice")
  .param("id", 100001)
  .execute();

// 事务 — lambda 边界即事务边界
db.transaction(tx -> {
    tx.sql("UPDATE accounts SET balance = balance - :amt WHERE id = :id")
      .param("amt", 100).param("id", fromId).execute();
    tx.sql("UPDATE accounts SET balance = balance + :amt WHERE id = :id")
      .param("amt", 100).param("id", toId).execute();
});
// 正常返回 → commit，抛异常 → rollback
```

**IoC 集成：**

```java
// DbModule 自动配置 — 只需要写配置
// application.yml:
//   freeway.db.url: jdbc:postgresql://...
//   freeway.db.username: app
//   freeway.db.password: secret

@Primary Database db;  // 注入默认数据库
```

**多数据源：**

```java
// Marker 方式 — 编译期类型安全
@Primary Database primary;
@ReadOnly Database replica;

// 配置前缀方式 — 运行时灵活
// freeway.db.datasources: replica, analytics
// freeway.db.datasources.analytics.url: jdbc:clickhouse://...
@Inject DbHub hub;
Database analytics = hub.get("analytics");
```

**Migration：**

```
src/main/resources/db/migration/
  V001__create_users.sql
  V002__add_email_column.sql
```

启动时自动按序执行，`_migrations` 表记录已跑版本，幂等安全。

**自定义 RowMapper：**

```java
@Contribute(RowMapperOverrides.class)
public static void myMappers(MappedConfiguration<Class<?>, RowMapper<?>> config) {
    config.add(MyJsonType.class, (rs, rowNum) ->
        MyJsonType.parse(rs.getString("payload")));
}
```

**关键特性：**

| 特性 | 说明 |
|------|------|
| 连接池 | Semaphore + ConcurrentLinkedDeque，内置零依赖 |
| 查询 | `#name` / `:name` 命名参数 + `?` 位置参数 |
| 映射 | Record（canonical constructor）和 JavaBean（MethodHandle setter） |
| 事务 | lambda 式 + try-with-resources 手动式 |
| 类型转换 | 可选注入 `TypeCoercer`，fallback 到内置 basicCoerce |
| Migration | ClassPathScanner 扫描 `db/migration/`，按序执行 |
| 多数据源 | `@Primary`/`@ReadOnly` marker + `Databases` hub |

---

## 项目状态

**活跃开发中。** 关键里程碑：

- IoC 容器 — 完整的服务生命周期、贡献机制、增强机制
- JSON — 零依赖
- Web — 基于 Handler 的路由、路径变量、过滤器链、异常映射
- Boot — YAML/properties/env/CLI 配置、优雅关闭
- DB 数据库访问 — 内置多数据源、连接池、事务、类型转换、Migration
---

*为 JDK 25+ 构建。没有 Spring。没有魔法。*
