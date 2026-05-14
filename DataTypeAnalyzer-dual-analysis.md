# `DataTypeAnalyzer` 双扩展点分析

## 现象

```java
@UsesOrderedConfiguration(DataTypeAnalyzer.class)           // 维度 A
@UsesMappedConfiguration(key = Class.class, value = String.class)  // 维度 B
public interface DataTypeAnalyzer {
    String identifyDataType(PropertyAdapter adapter);
}
```

同一个接口上同时标了两个扩展点注解。**框架能正确工作**，但对用户来说非常困惑。

## 这两个扩展点分别是什么

### 维度 A：`OrderedConfiguration<DataTypeAnalyzer>` — 分析器链

用户可以贡献多个 `DataTypeAnalyzer` 实现，形成一个**职责链**：

```java
// 用户模块：添加一个自定义分析器
@Contribute(DataTypeAnalyzer.class)
public static void addMyAnalyzer(OrderedConfiguration<DataTypeAnalyzer> analyzers) {
    analyzers.add("MyAnalyzer", new MyAnalyzer(), "before:Default");
}
```

运行时按序调用，直到某个分析器返回非 null 结果：
```
AnnotationAnalyzer → ... → DefaultDataTypeAnalyzer (最后一个)
```

### 维度 B：`MappedConfiguration<Class<?>, String>` — 内置分析器的类型映射表

用户可以为 `DefaultDataTypeAnalyzer` 贡献 **Java 类型 → 数据类型名** 的映射：

```java
// 用户模块：为自定义类型添加数据类型的映射
@Contribute(DataTypeAnalyzer.class)
public static void addTypeMapping(MappedConfiguration<Class<?>, String> mapping) {
    mapping.add(LocalDate.class, "date");
    mapping.add(BigDecimal.class, "number");
}
```

这些映射被 `DefaultDataTypeAnalyzer` 消费，`MappedConfiguration` 的收集结果通过构造器注入：

```java
public class DefaultDataTypeAnalyzer implements DataTypeAnalyzer {
    public DefaultDataTypeAnalyzer(Map<Class<?>, String> configuration) {
        // configuration = {String.class→"text", Number.class→"number", ...}
    }
}
```

## 它俩不冲突，但设计上有问题

### 为什么技术上不冲突

扩展点的识别是靠 `@Contribute` 方法的**参数类型**区分的：

| 贡献方法参数 | 收集方式 | 注入目标 |
|-------------|---------|---------|
| `OrderedConfiguration<DataTypeAnalyzer>` | 有序列表 | 服务 `DataTypeAnalyzer` 自身的构造器 / builder |
| `MappedConfiguration<Class<?>, String>` | 键值映射 | 服务 `DataTypeAnalyzer` 内部的子组件 |

框架的 `ContributionDefImpl` 根据参数类型路由到 `Configuration` / `OrderedConfiguration` / `MappedConfiguration` 三个不同的 `contribute()` 重载。**两者完全正交，不会互相干扰。**

```
用户写 @Contribute(DataTypeAnalyzer.class)
         │
         ├── void m(OrderedConfiguration<DataTypeAnalyzer> c)
         │       → contribute(..., OrderedConfiguration)
         │       → getOrderedConfiguration("DataTypeAnalyzer")
         │       → 注入给 DataTypeAnalyzer 的 builder
         │
         └── void m(MappedConfiguration<Class<?>, String> c)
                 → contribute(..., MappedConfiguration)
                 → getMappedConfiguration("DataTypeAnalyzer")
                 → 注入给 DefaultDataTypeAnalyzer 的构造器
```

### 为什么是设计上的异味

**根本问题：一个服务 ID 下混杂了两个完全不同的扩展意图。**

```
服务 "DataTypeAnalyzer"
├── 扩展意图 1：增加分析器实现
│   贡献类型：DataTypeAnalyzer（接口实现）
│   收集方式：ORDERED（排序）
│   对应代码：contributeDataTypeAnalyzer(...)
│
└── 扩展意图 2：增加类型→名称的映射
    贡献类型：String（数据类型名）
    收集方式：MAPPED（键=Class, 值=String）
    对应代码：provideDefaultDataTypeAnalyzers(...)
```

用户写 `@Contribute(DataTypeAnalyzer.class)` 时，无法一眼看出**这个 `@Contribute` 到底是在扩展什么**——是加分析器还是加类型映射？必须看方法参数才知道。

### 改进方向

方案一：拆成两个独立的扩展点（推荐）

```java
// 扩展点 1：分析器链
@ExtensionPoint(DataTypeAnalyzer.class)  // ORDERED
public interface DataTypeAnalyzer {
    String identifyDataType(PropertyAdapter adapter);
}

// 扩展点 2：默认分析器的类型映射表
@ExtensionPoint(value = String.class, collection = MAPPED, key = Class.class)
public interface DataTypeMappings {
    // 无需方法体，纯声明扩展点
}
```

方案二：保留一个扩展点，但改名让语义更明确

```java
// 明确声明"这是一个有序的 DataTypeAnalyzer 扩展点"
@ExtensionPoint(DataTypeAnalyzer.class)
public interface DataTypeAnalyzer { ... }

// DefaultDataTypeAnalyzer 内部的映射配置改为私有扩展点
// 或通过单独的 builder 方法接收
```

## 对其他 `@Uses*` 用法的启示

回到 `@ExtensionPoint` 的设计，`DataTypeAnalyzer` 的情况说明：

**`@Repeatable` 的设计是正确的**——确实存在一个服务需要多个扩展点的情况。但更好的做法是**拆成不同服务**，让每个服务只有一个清晰的扩展意图。
