# 用 `@ExtensionPoint` 统一 `@UsesOrderedConfiguration` / `@UsesMappedConfiguration`

## 一、现状问题

当前有两个声明注解，`RetentionPolicy.CLASS`，纯文档用途，框架运行时并不读取：

| 注解 | 参数 | 出现次数 |
|------|------|:--------:|
| `@UsesOrderedConfiguration(T)` | `value` — 贡献值类型 | 7 处 |
| `@UsesMappedConfiguration(K,V)` | `key` / `value` — 键类型 + 值类型 | 6 处 |

问题：
1. **概念分裂** — 用户只需知道"这是个扩展点"，不需要区分内部用 Ordered 还是 Mapped 实现
2. **命名不统一** — `UsesOrdered` / `UsesMapped` 描述的是内部机制而非设计意图

## 二、设计原则

- **最小化心智负担**：一个注解，没有配套容器注解，没有 `@Repeatable`
- **一个服务一个扩展意图**：如果一个服务需要两种不同的扩展方式，说明这个服务应该拆成两个服务
- 之前 `DataTypeAnalyzer` 同时使用两个注解，清理死代码后已解决这种特例

## 三、新注解设计

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
@Documented
@UseWith(AnnotationUseContext.SERVICE)
public @interface ExtensionPoint {

    /** 可被贡献的对象类型。 */
    Class<?> value();

    /** 收集方式。默认 ORDERED（最常用）。 */
    CollectionType collection() default CollectionType.ORDERED;

    /**
     * MAPPED 模式下的键类型。
     * <p>当 key == String.class 时，Map 为大小写不敏感。
     */
    Class<?> key() default String.class;

    enum CollectionType {
        /** 有序列表 — contributor 方法参数为 OrderedConfiguration<T> */
        ORDERED,
        /** 键值映射 — contributor 方法参数为 MappedConfiguration<K,V> */
        MAPPED
    }
}
```

### 为什么不需要 `@Repeatable`

`DataTypeAnalyzer` 是工程中唯一同时在一种服务上声明两种扩展点的案例。它的两个扩展意图（分析器链、类型映射表）本质上是两个不同职责，正解是拆成两个服务而不是用重复注解。该文件已被清理。

其余 12 处扩展点都只使用一种收集方式，单个 `@ExtensionPoint` 足够。

## 四、迁移映射

### ORDERED（7 处）

| 当前 | 改为 |
|------|------|
| `@UsesOrderedConfiguration(SymbolProvider.class)` | `@ExtensionPoint(SymbolProvider.class)` |
| `@UsesOrderedConfiguration(ServiceProvider.class)` | `@ExtensionPoint(ServiceProvider.class)` |
| `@UsesOrderedConfiguration(ServiceConfigurationListener.class)` | `@ExtensionPoint(ServiceConfigurationListener.class)` |
| `@UsesOrderedConfiguration(ExceptionMapper.class)` | `@ExtensionPoint(ExceptionMapper.class)` |
| `@UsesOrderedConfiguration(HttpFilter.class)` | `@ExtensionPoint(HttpFilter.class)` |
| `@UsesOrderedConfiguration(RouteDef.class)` | `@ExtensionPoint(RouteDef.class)` |

### MAPPED（6 处）

| 当前 | 改为 |
|------|------|
| `@UsesMappedConfiguration(String.class)` | `@ExtensionPoint(value = String.class, collection = MAPPED)` |
| `@UsesMappedConfiguration(key = CoercionTuple.Key.class, value = CoercionTuple.class)` | `@ExtensionPoint(value = CoercionTuple.class, collection = MAPPED, key = CoercionTuple.Key.class)` |
| `@UsesMappedConfiguration(key = Class.class, value = Object.class)` | `@ExtensionPoint(value = Object.class, collection = MAPPED, key = Class.class)` |
| `@UsesMappedConfiguration(ServiceLifecycle.class)` | `@ExtensionPoint(value = ServiceLifecycle.class, collection = MAPPED)` |
| `@UsesMappedConfiguration(key = Class.class, value = RowMapper.class)` | `@ExtensionPoint(value = RowMapper.class, collection = MAPPED, key = Class.class)` |

## 五、删除旧注解和死代码

迁移完成后：

```
freeway-annotations/src/.../UsesOrderedConfiguration.java  ✗ 删除
freeway-annotations/src/.../UsesMappedConfiguration.java   ✗ 删除
freeway-ioc/src/.../coercion/DataTypeAnalyzer.java         ✗ 已删（死代码）
freeway-ioc/src/.../internal/BasicDataTypeAnalyzers.java   ✗ 已删（死代码）
freeway-ioc/src/.../internal/DefaultDataTypeAnalyzer.java  ✗ 已删（死代码）
```

## 六、影响范围

| 文件 | 改动量 |
|------|:------:|
| **新建** `@ExtensionPoint` | 1 个文件 |
| **删除** `@UsesOrderedConfiguration` | - |
| **删除** `@UsesMappedConfiguration` | - |
| `SymbolSource.java` | 1 行 |
| `SymbolProvider.java` | 1 行 |
| `ObjectInjector.java` | 1 行 |
| `ServiceConfigurationListenerHub.java` | 1 行 |
| `TypeCoercer.java` | 1 行 |
| `ServiceOverride.java` | 1 行 |
| `ServiceLifecycleSource.java` | 1 行 |
| `RowMapperOverrides.java` | 1 行 |
| `ExceptionMapperChain.java` | 1 行 |
| `HttpFilterChain.java` | 1 行 |
| `RouteRegistry.java` | 1 行 |
| `AnnotationUseContext.java` | 清理旧引用 |
| `package-info.java` | 清理文档 |

总计：**1 个新注解，2 个删除注解，13 处简单替换**。零运行时行为变化。
