# FactoryDefaults & ApplicationDefaults 分析

## 优先级链

当前 `SymbolSource` 的 5 层提供者按优先级从高到低排列：

```
SystemProperties         最高  — JVM -D 参数
  ↓ 覆盖
EnvironmentVariables            — env.FREEWAY_XXX
  ↓ 覆盖
BootConfig (YAML + CLI)         — 仅 freeway-boot 模式
  ↓ 覆盖
ApplicationDefaults             — 用户模块的配置
  ↓ 覆盖
FactoryDefaults          最低  — 框架内置默认值
```

## 各层的实际贡献内容

### FactoryDefaults（3 处贡献，框架各模块的出厂设置）

| 模块 | 贡献的值 | 用途 |
|------|---------|------|
| `FreewayIOCModule.setupDefaultSymbols()` | 线程池 core=3, max=20, keepAlive=1m, queue=100, proxy=jdk | IoC 容器基础设置 |
| `DbModule.contributeDefaults()` | migration.enabled=true, migration.path=db/migration/ | DB 模块默认值 |
| `WebModule.contributeDefaults()` | port=8080, host=0.0.0.0, cors.\* 等 | Web 服务器默认值 |

### ApplicationDefaults（2 处贡献）

| 位置 | 贡献的值 | 用途 |
|------|---------|------|
| `BootConfigModuleDefinition` | YAML + CLI 合并配置 | 应用启动配置 |
| `DbModuleIntegrationTest` | 测试数据库 url/username/password | 测试用例 |

## 关键发现

### ① 两层的本质完全相同

`FactoryDefaults` 和 `ApplicationDefaults` 在机制上没有区别——都是 `@Contribute(SymbolProvider.class)` + `MappedConfiguration<String, Object>` 向一个 `MapSymbolProvider` 写入键值对。**唯一的差异就是优先级先后**。

`ApplicationDefaults` 本质上就是"在 `FactoryDefaults` 上面的那一层"，而不是一个语义不同的概念。

### ② ApplicationDefaults 名称不直观

从命名看，`ApplicationDefaults` 像是"应用级默认值"，但实际含义是"比 FactoryDefaults 优先级更高的一层配置"。新用户看到这两个名字，很难立刻理解它们的优先级关系。

对比更直观的命名方案：

| 当前 | → 更直观的方案 |
|------|---------------|
| `FactoryDefaults` | `Defaults` 或 `BuiltinDefaults` |
| `ApplicationDefaults` | `Overrides` 或 `AppConfig` |

### ③ FactoryDefaults 的设计意图正确，但实现位置分散

FactoryDefaults 的定位很清晰：**"让框架开箱即用，用户零配置也能跑"**。但它的贡献点散落在各个模块中（IoC/DB/Web 各自贡献自己的默认值），一旦某个模块忘记贡献，用户就会看到 `Symbol not defined` 错误。

### ④ ApplicationDefaults 的实际使用者几乎只有 BootConfig

除了测试代码，`@ApplicationDefaults` 标记只有一个真正的使用者——`BootConfigModuleDefinition`，它把 YAML 配置放在这一层。普通用户模块如果想让自己的配置能被系统属性覆盖，理论上也应该用 `@ApplicationDefaults`。

## 结论

| 项目 | 评估 |
|------|------|
| **设计合理吗？** | ✅ 分层优先级的设计是好的——让系统属性 > 环境变量 > 应用配置 > 框架默认值 |
| **命名清晰吗？** | ❌ `Factory` / `Application` 语义模糊，优先级关系不直观 |
| **实现一致吗？** | ⚠️ `ApplicationDefaults` 只有框架内部在用，用户模块几乎不知道它的存在 |
| **需要合并吗？** | 可以保持两层（优先级分层是有价值的），但建议改名让语义更直接 |

## 改进方向

保持两层是合理的设计，因为分层的优先级机制本身有价值。建议的方向是让命名更直观地反映优先级语义：

```
FactoryDefaults      →  Defaults        （框架出厂设置，最低优先级）
ApplicationDefaults  →  UserConfig      （用户配置，覆盖默认值）
```

这样用户一眼就能理解"低优先级的是框架默认值，高优先级的是我的配置"。
