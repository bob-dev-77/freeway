# Freeway Web 模块审计报告

审计范围：`freeway-web` 模块，覆盖路由、过滤器链、异常映射、请求上下文、HTTP 适配、启动集成、默认配置与测试覆盖。

审计前提：不考虑兼容性约束，仅从第一性原理评估设计正确性、边界清晰度、可诊断性、可测试性和长期演进成本。

## 总结

Freeway Web 已经是一个可用的 HTTP 适配层，但当前存在两个层面的风险：

1. 运行时层面有真实缺陷，主要集中在 shutdown、异常映射和路由语义上。
2. 结构层面开始承担过多职责，`WebModule` 既是装配入口，又是默认配置入口、启动入口和部分运行时策略入口。

它现在不是“不能用”，而是“行为还不够硬，边界还不够清楚”。对于一个框架 web 模块，这会直接影响用户对路由、过滤器、异常处理和扩展点的信任。

## 结论评级

- 设计完整性：6/10
- 路由语义清晰度：5/10
- 错误处理质量：4/10
- 资源管理：4/10
- 可测试性：6/10
- 结构稳定性：5/10

综合判断：**可用，但需要优先修正几个运行时问题，并收紧默认行为和边界语义。**

## 关键问题

### 1. `WebModule` 创建的 Executor 没有被关闭

`startWebServer()` 中使用了 `Executors.newVirtualThreadPerTaskExecutor()`，但 shutdown 逻辑只调用了 `server.stop(grace)`，没有关闭 executor。这个 executor 是显式创建出来的资源，应该有对应的关闭动作。

证据：

- [WebModule.java](C:/Users/Z13/Projects/freeway/freeway-web/src/main/java/com/jujin/freeway/web/WebModule.java:128)
- [WebModule.java](C:/Users/Z13/Projects/freeway/freeway-web/src/main/java/com/jujin/freeway/web/WebModule.java:154)

影响：

- 退出时可能残留资源。
- Web 模块的生命周期不完整。
- 在长期运行或重复启动测试中，容易放大资源泄漏风险。

建议：

- 将 executor 注册到 shutdown hub，和 server 一起关闭。
- 如果 executor 只是为了简单并发执行，考虑把关闭动作写成模块级标准流程，而不是分散在 startup 内部。

### 2. `handleException()` 没有保护 exception mapper 自身

`handleException()` 会依次调用 mapper，但没有对 `mapper.handle()` 再做保护。如果某个 mapper 自己抛异常，当前请求会直接失败，fallback 的 `500` 响应也不会被保证执行。

证据：

- [WebModule.java](C:/Users/Z13/Projects/freeway/freeway-web/src/main/java/com/jujin/freeway/web/WebModule.java:185)

影响：

- 异常映射从“兜底机制”退化成“另一个故障点”。
- 处理链不够鲁棒。

建议：

- 对每个 mapper 增加隔离，mapper 抛出的异常应记录并继续尝试下一个 mapper，或至少进入统一 fallback。
- 异常映射的契约应明确：mapper 自身不得破坏响应生成路径。

### 3. 路由匹配是严格顺序优先，存在隐式遮蔽风险

`RouteRegistry.match()` 按注册顺序逐个匹配，先命中的路由直接返回。它没有按路径具体性做排序，也没有冲突检测。因此，某些更泛的路径可能先于更具体的路径被命中，行为完全依赖注册顺序。

证据：

- [RouteRegistry.java](C:/Users/Z13/Projects/freeway/freeway-web/src/main/java/com/jujin/freeway/web/RouteRegistry.java:59)
- [RouteRegistry.java](C:/Users/Z13/Projects/freeway/freeway-web/src/main/java/com/jujin/freeway/web/RouteRegistry.java:65)

影响：

- 路由行为不是结构化确定的，而是顺序依赖的。
- 用户很难直觉判断哪个路由会生效。
- 一旦模块多起来，冲突会变成隐性 bug。

建议：

- 明确路由优先级策略，要么按具体性排序，要么在注册阶段检测冲突并失败。
- 如果坚持顺序优先，就必须把这个规则写成框架契约，并补测试覆盖。

### 4. 路由重复语义不一致

`RouteRegistry.addRoute()` 会对重复路径发出 warn 并忽略后者，但构造函数在装配 contributed routes 时只是直接加入，不做同等级别的冲突约束。结果就是，不同来源的重复路由语义不完全一致。

证据：

- [RouteRegistry.java](C:/Users/Z13/Projects/freeway/freeway-web/src/main/java/com/jujin/freeway/web/RouteRegistry.java:44)
- [RouteRegistry.java](C:/Users/Z13/Projects/freeway/freeway-web/src/main/java/com/jujin/freeway/web/RouteRegistry.java:50)

影响：

- 同一个路径在不同入口下有不同的冲突处理方式。
- 框架语义不统一。

建议：

- 统一重复路由策略。
- 更稳妥的做法是启动期直接报错，而不是静默 shadow。

### 5. `CorsFilter` 的程序化 builder 默认行为与文档不一致

`CorsFilter.builder().build()` 在当前实现里并不会自动允许跨域，除非显式调用 `allowAllOrigins()` 或 `allowedOrigins(...)`。但 builder 注释和用户直觉容易把它理解为“默认即有可用 CORS 行为”。与此同时，IoC 默认配置又把 `cors.allowed-origins` 设成了 `"*"`, 两条路径的默认值不统一。

证据：

- [CorsFilter.java](C:/Users/Z13/Projects/freeway/freeway-web/src/main/java/com/jujin/freeway/web/CorsFilter.java:97)
- [CorsFilter.java](C:/Users/Z13/Projects/freeway/freeway-web/src/main/java/com/jujin/freeway/web/CorsFilter.java:101)
- [WebModule.java](C:/Users/Z13/Projects/freeway/freeway-web/src/main/java/com/jujin/freeway/web/WebModule.java:71)

影响：

- 同一组件存在两套默认值。
- 用户很容易误判 builder 的开箱行为。

建议：

- 统一 builder 默认值和 IoC 默认值。
- 如果默认就应允许跨域，builder 也应显式体现。
- 如果默认不应允许跨域，就把文档改成明确的安全默认。

### 6. `JdkHttpContext` 对请求体大小的处理缺少更硬的契约

`body()` 会按 `maxBodySize` 读取请求体，如果超出限制就抛异常。这个方向是对的，但当前没有形成更上层的统一错误响应约定。也就是说，限制生效了，但“限制触发后怎么回给客户端”还依赖外层链路。

证据：

- [JdkHttpContext.java](C:/Users/Z13/Projects/freeway/freeway-web/src/main/java/com/jujin/freeway/web/JdkHttpContext.java:79)
- [JdkHttpContext.java](C:/Users/Z13/Projects/freeway/freeway-web/src/main/java/com/jujin/freeway/web/JdkHttpContext.java:85)

影响：

- 上传过大时的用户体验取决于外层异常映射是否正确。

建议：

- 给 body size 超限建立固定的异常类型和默认 mapper。

## 结构性问题

### 7. `WebModule` 职责过重

它同时承担：

- 服务绑定
- 默认配置
- 自动加入 CORS filter
- HTTP server 启动
- health route 注入
- 运行时过滤器链拼装
- 异常映射

这已经超过一个模块应该承载的职责密度。

证据：

- [WebModule.java](C:/Users/Z13/Projects/freeway/freeway-web/src/main/java/com/jujin/freeway/web/WebModule.java:62)
- [WebModule.java](C:/Users/Z13/Projects/freeway/freeway-web/src/main/java/com/jujin/freeway/web/WebModule.java:75)
- [WebModule.java](C:/Users/Z13/Projects/freeway/freeway-web/src/main/java/com/jujin/freeway/web/WebModule.java:93)
- [WebModule.java](C:/Users/Z13/Projects/freeway/freeway-web/src/main/java/com/jujin/freeway/web/WebModule.java:102)

影响：

- 模块越来越像启动器，而不是纯 web 装配层。
- 以后想替换 server、router 或默认策略时，会越来越难拆。

建议：

- 将启动逻辑拆到独立的 `WebServerStarter` 或类似类中。
- 将默认配置与运行时拼装分离。
- `WebModule` 只保留绑定和贡献点定义。

### 8. `RouteRegistry` 把路由、匹配、参数解析、路径安全策略都放在一个类里

这个类目前既是注册表，又是匹配器，又是 path parser。对于当前规模，它还能工作，但已经不适合继续膨胀。

证据：

- [RouteRegistry.java](C:/Users/Z13/Projects/freeway/freeway-web/src/main/java/com/jujin/freeway/web/RouteRegistry.java:23)

建议：

- 把 path pattern 解析和 route match 提取成独立的值对象或 matcher。
- 让 `RouteRegistry` 更像一个薄容器，而不是一个全能类。

### 9. `HttpContext` 的抽象层已经很实，但仍有一些默认行为没有形成显式契约

例如 `responseHeader()` 默认返回 `null`，`bodyAsJson()` 对 Content-Type 的检查是“包含 json 即通过”，`send()` 对 204/304 只是跳过 Content-Type 设置。设计本身可以接受，但这些都属于“隐式规则”，需要更明确地写入契约和测试。

证据：

- [HttpContext.java](C:/Users/Z13/Projects/freeway/freeway-web/src/main/java/com/jujin/freeway/web/HttpContext.java:100)
- [HttpContext.java](C:/Users/Z13/Projects/freeway/freeway-web/src/main/java/com/jujin/freeway/web/HttpContext.java:132)
- [HttpContext.java](C:/Users/Z13/Projects/freeway/freeway-web/src/main/java/com/jujin/freeway/web/HttpContext.java:145)

建议：

- 明确哪些行为是 server 适配器必须支持的，哪些是默认实现的便利行为。
- 关键默认行为要有测试和文档。

## 测试缺口

当前测试覆盖了路由匹配、CORS、和一部分端到端 HTTP 请求，但对以下场景覆盖不足：

- `ExceptionMapper` 自身抛异常
- 多个冲突路由的优先级语义
- `WebModule` shutdown 时 executor 的清理
- route 具体性和顺序的冲突
- 请求体超限的失败路径
- CORS builder 和 IoC 默认值的一致性

对于 web 模块，测试不仅要证明“能返回 200”，还要证明“失败时怎么失败”。

## 改进路线

### 第一阶段：修复运行时问题

- 关闭 `newVirtualThreadPerTaskExecutor()` 对应的 executor。
- 给 `handleException()` 的 mapper 执行增加隔离。
- 明确路由冲突的失败或覆盖策略。

### 第二阶段：统一默认语义

- 统一 `CorsFilter` builder 默认值和 IoC 默认值。
- 统一重复路由的处理方式。
- 将隐式行为写入文档和测试。

### 第三阶段：拆职责

- 把 server 启动逻辑从 `WebModule` 拆出去。
- 把路由解析拆成 matcher/registry 两层。
- 把异常映射与默认 fallback 处理分离。

### 第四阶段：补边界

- 为 body size、路由冲突、mapper 失败增加稳定错误类型。
- 明确 request/response 适配器接口的最小职责。

## 结语

Freeway Web 的方向是对的：用轻量 server + IoC 组合出一个可读、可扩展的 REST 层。但现在最该做的不是继续加语法糖，而是把行为规则固定下来，让路由、过滤器和异常处理都有硬契约。否则 web 层会很快变成“看上去简单，实际上依赖细节”的脆弱系统。

---

本报告基于当前源码静态审计生成，未修改业务实现。
