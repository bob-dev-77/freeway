# Freeway 工程审计报告

审计范围：`freeway` 多模块工程的工程结构、代码组织、命名、公开 API、错误处理与测试覆盖。

审计方法：基于源码静态阅读与目录扫描，不依赖兼容性约束，按第一性原理判断框架的可维护性、可诊断性和 API 可信度。

## 结论摘要

Freeway 已经具备一个可运行的 Java 框架骨架，但当前更像“功能集合”而不是“边界清晰的框架产品”。主要问题集中在四类：

1. 契约与实现不一致，尤其是 `FreewayBootEntry` 的语义与实际启动逻辑不匹配。
2. 错误处理倾向于静默吞掉异常，导致问题从“可定位”变成“不可解释”。
3. `internal` 包中的实现类大量 `public` 暴露，框架边界没有真正收紧。
4. 核心模块职责过重，命名与结构存在明显的历史包袱和术语漂移。

从框架产品的角度看，当前最大风险不是“代码不能跑”，而是“开发者无法建立稳定心智模型”。

## 评分

- 工程结构：6/10
- 代码组织：5/10
- 命名一致性：4/10
- API 清晰度：5/10
- 错误诊断能力：3/10
- 测试覆盖质量：5/10

综合判断：**中高风险，适合进入重构整理期**。

## 主要发现

### 1. 启动契约不诚实

`FreewayBootEntry` 的 Javadoc 声称它会触发自动发现并加载 auto-configuration，但 `FreewayApplication.run()` 实际只做了三件事：加载配置、手动加入主类、执行 `builder.autoDiscover()`。代码并没有检查 `@FreewayBootEntry` 注解本身，因此这个注解目前只是文档承诺，不是运行时契约。

证据：

- [FreewayBootEntry.java](C:/Users/Z13/Projects/freeway/freeway-boot/src/main/java/com/jujin/freeway/boot/FreewayBootEntry.java:9)
- [FreewayApplication.java](C:/Users/Z13/Projects/freeway/freeway-boot/src/main/java/com/jujin/freeway/boot/FreewayApplication.java:25)
- [FreewayApplication.java](C:/Users/Z13/Projects/freeway/freeway-boot/src/main/java/com/jujin/freeway/boot/FreewayApplication.java:65)

影响：

- 用户会误判“加了注解就会生效”。
- 框架契约不稳定，未来容易形成更多隐式行为。

建议：

- 要么把注解升级为真实入口校验，要么删除。
- 启动失败时应给出明确错误，而不是默认继续。

### 2. 配置和关闭流程静默吞异常

`JsonConfigProvider`、`PropertiesConfigProvider` 都在捕获 `Exception` 后直接忽略。`FreewayAppImpl.shutdown()` 也是同样模式。对框架而言，这会把最重要的错误信息抹掉。

证据：

- [JsonConfigProvider.java](C:/Users/Z13/Projects/freeway/freeway-boot/src/main/java/com/jujin/freeway/boot/config/JsonConfigProvider.java:54)
- [PropertiesConfigProvider.java](C:/Users/Z13/Projects/freeway/freeway-boot/src/main/java/com/jujin/freeway/boot/config/PropertiesConfigProvider.java:31)
- [FreewayAppImpl.java](C:/Users/Z13/Projects/freeway/freeway-boot/src/main/java/com/jujin/freeway/boot/FreewayAppImpl.java:27)

影响：

- 坏配置会被误判为“缺省配置”。
- shutdown 失败不可见，导致资源泄漏或退出异常难以追踪。
- 调试成本显著上升。

建议：

- 不要无声吞异常。
- 允许降级，但必须记录来源、原因和处理结果。
- shutdown 应保留日志，必要时向上抛出。

### 3. `internal` 包没有真正内部化

`ioc/internal`、`db/internal` 中大量类是 `public`。这意味着“internal”只是在命名上暗示内部实现，并没有形成 API 边界。外部用户会自然依赖这些类，后续重构会被实现细节锁死。

证据：

- `freeway-ioc/src/main/java/com/jujin/freeway/ioc/internal/RegistryImpl.java`
- `freeway-ioc/src/main/java/com/jujin/freeway/ioc/internal/ParallelExecutorImpl.java`
- `freeway-db/src/main/java/com/jujin/freeway/db/internal/ConnectionPool.java`
- `freeway-db/src/main/java/com/jujin/freeway/db/internal/DatabaseImpl.java`
- `freeway-db/src/main/java/com/jujin/freeway/db/internal/DbHubImpl.java`

影响：

- public API 面不断膨胀。
- 用户很难分辨哪些是稳定入口，哪些是实现细节。
- 模块重构成本高。

建议：

- internal 目录下的实现优先改为 package-private。
- 公共 API 只保留接口、工厂、少量入口类。
- 后续补 `module-info.java`，把 `exports` 和 `opens` 收紧。

### 4. 核心模块职责过重

`FreewayIOCModule` 同时承担绑定、生命周期、对象注入、类型转换、符号源、并发执行和启动注册等职责。`WebModule` 也把绑定、默认配置、服务启动、过滤器链构造、异常处理放在一个类里。模块已经不是“模块”，而是“功能拼盘”。

证据：

- [FreewayIOCModule.java](C:/Users/Z13/Projects/freeway/freeway-ioc/src/main/java/com/jujin/freeway/ioc/FreewayIOCModule.java:43)
- [FreewayIOCModule.java](C:/Users/Z13/Projects/freeway/freeway-ioc/src/main/java/com/jujin/freeway/ioc/FreewayIOCModule.java:45)
- [FreewayIOCModule.java](C:/Users/Z13/Projects/freeway/freeway-ioc/src/main/java/com/jujin/freeway/ioc/FreewayIOCModule.java:149)
- [FreewayIOCModule.java](C:/Users/Z13/Projects/freeway/freeway-ioc/src/main/java/com/jujin/freeway/ioc/FreewayIOCModule.java:304)
- [WebModule.java](C:/Users/Z13/Projects/freeway/freeway-web/src/main/java/com/jujin/freeway/web/WebModule.java:56)
- [WebModule.java](C:/Users/Z13/Projects/freeway/freeway-web/src/main/java/com/jujin/freeway/web/WebModule.java:102)

影响：

- 阅读成本高。
- 单元测试很难按职责切分。
- 未来功能增加会继续向“巨型类”堆积。

建议：

- 按职责拆出更小的模块或装配类。
- 将启动逻辑、默认配置、生命周期注册分别下沉到独立类。

### 5. 命名体系存在明显不一致

最明显的问题是 `PerthreadManager` / `PerthreadManagerImpl`，与正常的 `PerThreadManager` 命名不一致。类似问题还包括注释中的包名引用不统一、个别类名/术语沿用了历史语义。

证据：

- [PerthreadManager.java](C:/Users/Z13/Projects/freeway/freeway-ioc/src/main/java/com/jujin/freeway/ioc/threading/PerthreadManager.java:14)
- [ModuleProvider.java](C:/Users/Z13/Projects/freeway/freeway-ioc/src/main/java/com/jujin/freeway/ioc/ModuleProvider.java:14)

影响：

- 检索、补全、沟通成本增加。
- 命名不统一会削弱框架的专业感。

建议：

- 优先修正公开 API 和用户可见术语。
- 建立统一词汇表，后续命名必须遵循。

### 6. 面向 JDK 25+ 的定位尚未体现在模块化边界上

仓库中未发现 `module-info.java`。这意味着工程仍然主要依赖 classpath 约定，而不是模块系统本身的约束能力。对于一个面向 JDK 25+ 的框架，这与产品定位不完全一致。

影响：

- 无法显式表达 `exports`、`requires`、`uses`、`provides`、`opens`。
- SPI 和反射边界只能靠约定。

建议：

- 至少从 `ioc`、`boot`、`web`、`db` 开始补模块描述。

## 次要发现

1. 仓库中存在构建产物痕迹，例如各模块的 `target/` 目录，以及 `freeway-web/cp.txt`。这些内容应确保不进入版本控制或在 CI 中清理。
2. `freeway-boot-starter` 目前只是一个空的 POM 聚合器，若无明确发行目标，容易成为结构噪音。
3. `WebModule` 的健康检查失败路径只打 warn，没有明确兜底响应，属于可用性细节问题。
4. 测试更偏正常路径，针对失败语义、错误输入和契约违背的覆盖不足。

## 结构性改进方案

### 第一阶段：修正契约

- 明确 `FreewayBootEntry` 的真实语义。
- 把静默吞异常改成可诊断失败。
- 为启动、配置加载、关闭流程补失败测试。

### 第二阶段：收紧边界

- 降低 `internal` 下 `public` 实现类的暴露面。
- 收敛公开 API，只留下真正需要给用户依赖的类型。
- 逐步补 `module-info.java`。

### 第三阶段：拆分职责

- 将 `FreewayIOCModule` 拆成装配入口和若干功能子模块。
- 将 `WebModule` 的启动、路由、过滤器、异常处理拆分。
- 把 boot 配置读取抽成独立配置层，不再和启动流程混在一起。

### 第四阶段：统一命名

- 修正 `Perthread*` 的拼写。
- 清理陈旧或不一致的包名引用。
- 建立全项目术语表，作为后续新增 API 的约束。

## 风险判断

如果继续按当前形态扩展，短期内功能会继续增加，但长期会出现三个典型后果：

1. 用户不知道哪些 API 可以依赖。
2. 框架内部问题难以定位，支持成本升高。
3. 重构窗口越来越窄，最后只能以兼容性成本换结构改造。

## 建议的下一步

先处理启动契约、异常处理和内部边界三个点。这三项的收益最高，且会直接改善框架的可信度。

---

本报告基于当前源码静态审计，未修改业务代码。
