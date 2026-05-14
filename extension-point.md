# Freeway 扩展点（Extension Point）实现要素

## 一、什么是扩展点

扩展点是框架中**允许第三方代码向一个服务贡献值**的位置。用户通过模块中的 `@Contribute` 方法向扩展点添加值，框架在运行时收集所有贡献并注入给该服务。

## 二、三类收集方式

| 方式 | 接口 | 适用场景 | 典型用途 |
|------|------|---------|---------|
| **无序集合** | `Configuration<T>` | 值的顺序不重要 | 类型映射、符号值 |
| **有序列表** | `OrderedConfiguration<T>` | 值的顺序重要 | 过滤器链、路由、符号源 |
| **键值映射** | `MappedConfiguration<K,V>` | 通过唯一键存取 | 类型映射、配置覆盖 |

## 三、实现一个扩展点的要素

以 `HttpFilterChain` 为例，一个完整的扩展点需要 **4 个角色**：

```
声明端（服务定义方）                消费端（用户模块）
┌─────────────────────┐          ┌─────────────────────────┐
│ @UsesOrderedConfig  │          │ @Contribute(HttpFilter  │
│   (HttpFilter.class)│          │   Chain.class)          │
│ record HttpFilter   │          │ public static void      │
│   Chain(...)        │          │   addFilter(...)        │
│         ↑           │          │     ↑                   │
│         │ 收集      │          │     │ 贡献              │
│         │           │          │     │                   │
│    RegistryImpl     │ ←─────── │ ordered.add("myId",    │
│    (运行时聚合)      │          │   new MyFilter(),      │
└─────────────────────┘          │   "after:Auth")        │
                                 └─────────────────────────┘
```

### 要素 1：声明注解 `@UsesOrderedConfiguration` / `@UsesMappedConfiguration`

作用在服务类/接口上，**声明这是一个扩展点**，告诉框架和用户"这个服务接受贡献"。

```java
@UsesOrderedConfiguration(HttpFilter.class)
public record HttpFilterChain(List<HttpFilter> filters) { ... }
```

| 注解 | 含义 | 用户贡献方式 |
|------|------|-------------|
| `@UsesOrderedConfiguration(T)` | 接受有序列表 | `OrderedConfiguration<T>` |
| `@UsesMappedConfiguration(K,V)` | 接受键值映射 | `MappedConfiguration<K,V>` |

### 要素 2：用户贡献方法 `@Contribute` + 命名约定

用户在模块中写一个 `contributeXxx` 方法或加 `@Contribute` 注解，参数类型决定收集方式。

```java
// 方式 A：命名约定（方法名以 contribute 开头）
public static void contributeHttpFilterChain(
    OrderedConfiguration<HttpFilter> config) {
    config.add("Auth", new AuthFilter(), "before:*");
    config.add("Logging", new LoggingFilter(), "after:Auth");
}

// 方式 B：@Contribute 注解
@Contribute(HttpFilterChain.class)
public static void addMyFilter(OrderedConfiguration<HttpFilter> config) {
    config.add("MyFilter", new MyFilter(), "after:Auth");
}
```

**参数类型决定收集方式**：

| 贡献方法参数类型 | 收集方式 |
|------------------|---------|
| `Configuration<T>` | 无序集合 |
| `OrderedConfiguration<T>` | 有序列表 |
| `MappedConfiguration<K,V>` | 键值映射 |

### 要素 3：运行时收集（框架自动完成）

`RegistryImpl` 在处理模块时自动执行所有 `@Contribute` 方法，收集值并注入。

```java
// RegistryImpl 内部（简化）
for (ContributionDef def : contributions) {
    OrderedConfiguration<T> config = new ValidatingOrderedConfigurationWrapper<>(...);
    def.contribute(module, resources, config);   // 调用用户的 contribute 方法
}
// config 中已收集所有用户贡献的值
```

### 要素 4：服务自身消费收集结果

服务（`HttpFilterChain`）的构造器参数或 builder 方法中接收收集好的值。

```java
// 在 RegistryImpl.getOrderedConfiguration() 完成后，
// 框架将收集的结果注入给服务构造器或 builder 方法

// 方式 A：record 构造器（推荐）
@UsesOrderedConfiguration(HttpFilter.class)
public record HttpFilterChain(List<HttpFilter> filters) {
    public HttpFilterChain { filters = List.copyOf(filters); }
}

// 方式 B：builder 方法
public static HttpFilterChain build(OrderedConfiguration<HttpFilter> config) {
    return new HttpFilterChain(config);  // 参数实际类型是 List/Map
}
```

## 四、当前工程中哪些是扩展点

### 有 @Uses* 声明的扩展点（13 个）

| 服务 | 注解 | 贡献类型 | 使用方 |
|------|------|---------|--------|
| `SymbolSource` | `@UsesOrderedConfiguration` | `SymbolProvider` | 6 个 provider |
| `ObjectInjector` | `@UsesOrderedConfiguration` | `ServiceProvider` | 3 个 provider |
| `ServiceConfigurationListenerHub` | `@UsesOrderedConfiguration` | `ServiceConfigurationListener` | 框架内部 |
| `ExceptionMapperChain` | `@UsesOrderedConfiguration` | `ExceptionMapper` | Web 模块 |
| `HttpFilterChain` | `@UsesOrderedConfiguration` | `HttpFilter` | Web 模块 |
| `RouteRegistry` | `@UsesOrderedConfiguration` | `RouteDef` | Web 模块 |
| `SymbolProvider` | `@UsesMappedConfiguration` | `String→String` | 3 处贡献 |
| `TypeCoercer` | `@UsesMappedConfiguration` | `CoercionTuple` | 2 处贡献 |
| `ServiceOverride` | `@UsesMappedConfiguration` | `Class→Object` | 框架内部 |
| `ServiceLifecycleSource` | `@UsesMappedConfiguration` | `String→ServiceLifecycle` | 1 处贡献 |
| `RowMapperOverrides` | `@UsesMappedConfiguration` | `Class→RowMapper` | 用户模块 |
| `DataTypeAnalyzer` | `@UsesMappedConfiguration` | `Class→String` | 框架内部 |
| `DataTypeAnalyzer` | `@UsesOrderedConfiguration` | `DataTypeAnalyzer` | 框架内部 |

### 无 @Uses* 但通过 @Contribute 贡献的服务

| 服务 | 收集方式 | 说明 |
|------|---------|------|
| `RegistryStartup` | `OrderedConfiguration<Runnable>` | `contributeRegistryStartup()` |
| `ConfigSource` (boot) | `OrderedConfiguration<ConfigSource>` | `BootConfigModuleDefinition` 直接贡献 |

## 五、实现一个扩展点的步骤清单

如果要新增一个扩展点，按以下步骤：

```
步骤 1 ── 定义贡献值类型
           interface MyPlugin { ... }

步骤 2 ── 定义扩展点服务
           @UsesOrderedConfiguration(MyPlugin.class)
           public record MyExtensionPoint(List<MyPlugin> plugins) {
               public MyExtensionPoint { plugins = List.copyOf(plugins); }
           }

步骤 3 ── （可选）builder 方法接收贡献
           如果服务不是 record，用 buildXxx 方法：
           public static MyService build(OrderedConfiguration<MyPlugin> config) {
               return new MyServiceImpl(config);
           }

步骤 4 ── 用户在模块中贡献
           @Contribute(MyExtensionPoint.class)
           public static void addPlugin(OrderedConfiguration<MyPlugin> config) {
               config.add("myPlugin", new MyPluginImpl(), "before:*");
           }
```

框架自动完成：`DefaultModuleDefinition` 识别 `contribute` 方法 → `ContributionDefImpl` 桥接到 `RegistryImpl` → `RegistryImpl` 调用 `getOrderedConfiguration` 收集所有贡献 → 值注入给服务。
