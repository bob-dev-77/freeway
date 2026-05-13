English | [中文](README.zh.md)

# Freeway Application Framework

**Standalone IoC & AOP + Boot + Web + DB — pure Java, composition-first.**

Freeway is a modern, lightweight Java application framework built for **JDK 25+** with a singular philosophy:

> **Maximum JDK's own ability.**

| Module | Description |
|--------|-------------|
| `freeway-annotations` | IoC annotations (`@Inject` `@Advice` `@Contribute`, `@Startup`, `@Symbol`, etc.) |
| `freeway-commons` | JSON parser/generator, logging adapters |
| `freeway-ioc` | IoC container core, type coercion, resource utilities |
| `freeway-db` | Database access — built-in connection pool, named params, Record/Bean mapping, migrations |
| `freeway-web` | Handler-only web layer (`FreewayContext`, `RouteRegistry`, exception mappers, JDK HttpServer + robaho httpserver, virtual threads) |
| `freeway-boot` | Bootstrap, external configuration (YAML, properties, env, CLI) |
| `freeway-boot-starter` | POM aggregator (convenience dependency) |

---

## Philosophy

1. **Sharp teams (3–5 people) build sharp application** — build CLI/WEB applications.
2. **Not a quest for "bigger and fuller"** — no unnecessary abstractions, no bloated config.
3. **JDK's-ability-first** — fully leverages JDK's features (records, sealed types, pattern matching, virtual threads). Zero legacy compatibility burden.
4. **External dependencies are a last resort** — every dependency must earn its place.
5. **No reflection-driven magic** — service registration happens via static methods on module classes, clearly visible and debuggable.

---

## Quick Start

### Maven

```xml
<dependency>
    <groupId>com.jujin</groupId>
    <artifactId>freeway-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Your first Freeway application

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

### With Web

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

### With JSON (built into `freeway-commons`)

```java
// Serialize
String json = JSONUtils.toJson(Map.of("name", "Freeway", "version", 1.0));
// → {"name":"Freeway","version":1.0}

// Parse
JSONObject obj = (JSONObject) JSONUtils.fromJson(json);
String name = obj.getString("name");  // "Freeway"

// Typed deserialize
MyBean bean = JSONUtils.fromJson(json, MyBean.class);
```

---

## Architecture

### Modules

```
freeway-annotations    →  @Startup, @Contribute, @Inject, @Autobuild, @Symbol, @Value etc.
freeway-commons        →  JSON (zero-dependency parser/generator), logging adapters, type coercion
freeway-ioc            →  Module-Services architecture, SPI extension, powerful IoC & AOP ability
freeway-web            →  WebModule — pure Handler routing, JDK HttpServer + robaho httpserver on virtual threads
freeway-boot           →  FreewayApplication — config loader + startup
freeway-boot-starter   →  POM aggregator
```

### Core Concepts — Module

Freeway IoC organizes the entire container around **Module** as the fundamental unit. A Module is a plain Java class that declares services, enhancements, and contributions through **method naming conventions** or **annotations** — both first-class, equally supported:

```java
// Pure naming convention
buildGreeter()             → service definition
adviseGreeter()            → service enhancement
contributeToGreeter()      → service contribution
startup()                  → lifecycle callback

// Annotation driven (equivalent)
@Build  Greeter buildGreeter()
@Advise(serviceInterface = Greeter.class) void adviseGreeter(...)
@Contribute(Greeter.class) void contributeToGreeter(...)
@Startup void onStart()
```

Modules register via `ModuleProvider` SPI (JDK `ServiceLoader`) and form explicit composition trees through `@ImportModule`.

---

### IoC Container — Five Core Abilities

#### 1. Service Definition — `build`

A `build*` method declares a service with a unique **service ID**, an **interface**, and a **scope**:

| Element | Description |
|---------|-------------|
| `ServiceDef` | Service descriptor (ID, interface, scope, markers) |
| `ServiceBinder` | Declarative binding (Guice-style `bind(A, AImpl)`) |
| `@Scope` | Lifecycle: `"singleton"` (default), `"perthread"`, custom |
| `@EagerLoad` | Instantiate at startup, not lazily |
| `@Primary` | Mark preferred implementation among multiple |
| `@Marker` | Type-safe annotation-based qualifiers |
| `ServiceLifecycle` | Custom scope implementation |

```java
// Three ways to define a service:
public Greeter buildGreeter() { return new GreeterImpl(); }

public void bind(ServiceBinder binder) {
    binder.bind(Greeter.class, GreeterImpl.class);
}

// Via Registry.Builder
registry.addService(Greeter.class, ctx -> new GreeterImpl());
```

#### 2. Service Advising — `advise` (Method-Level Interception)

An `advise*` method (or `@Advise`) declares **method-level interceptors** on target services. This is Freeway IoC's single, unified AOP pipeline — no `@Aspect`, no `@Around/@Before/@After`, no pointcut expressions:

```
@Advise  annotated method
  → AdvisorDef (definition, matches which services)
    → ServiceAdvisor.advise(MethodAdviceReceiver)
      → receiver.adviseMethod("methodName", MethodAdvice)
        → MethodAdvice.advise(MethodInvocation)  ◄── single callback
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
        advisor.adviseMethod("farewell", /* same pattern */);
    }
}
```

Key elements:

| Element | Purpose |
|---------|---------|
| `@Advise(serviceInterface)` | Annotate an advisor method, bound to a service type |
| `AdvisorDef` | Definition: `matches(ServiceDef)` filter |
| `ServiceAdvisor` | Advising actor: registers Advice on `MethodAdviceReceiver` |
| `MethodAdvice` | `@FunctionalInterface` — single callback with `proceed()` |
| `MethodAdviceReceiver` | Register Advice per **method name** |
| `@Order` | Control execution order among multiple Advisors |
| `@PreventServiceDecoration` | Suppress proxy generation (no advising) |

Multiple Advisors stack into an interceptor chain:

```
serviceProxy.method()
  → Advisor A (outer)
    → Advisor B (inner)
      → original service.method()
```

No `@Decorate`, no `@Match`, no dual-path confusion — a single, precise `@Advise` → `MethodAdvice` pipeline.

#### 3. Service Contribution — `contribute` (Plugin Extension)

A `contribute*` method (or `@Contribute`) injects data into **another service's configuration**. This is Freeway's most distinctive **plugin-style extension mechanism**:

```java
// Target declares it accepts hook-ins
@UsesConfiguration(Interceptor.class)
public interface GreeterInterceptorSource { }

// Any module anywhere can contribute
@Contribute(GreeterInterceptorSource.class)
public static void contributeToGreeter(Configuration<Interceptor> config) {
    config.add(new LoggingInterceptor());
    config.add(new MetricsInterceptor());
}
```

Three configuration shapes:

| Shape | Interface | Behavior |
|-------|-----------|----------|
| Unordered | `Configuration<T>` | Add-only, no ordering |
| Ordered | `OrderedConfiguration<T>` | Each item has a unique ID + relative positioning (`before/after`) |
| Keyed | `MappedConfiguration<K,V>` | Unique-key map, duplicate key = error |

Annotations:

| Annotation | Purpose |
|------------|---------|
| `@UsesConfiguration` | Declare unordered configuration slot |
| `@UsesOrderedConfiguration` | Declare ordered configuration slot |
| `@UsesMappedConfiguration` | Declare key-value configuration slot |
| `@Optional` | Contribution is optional; skip if target service absent |

#### 4. Automatic Assembly — `@Inject`

Freeway IoC provides a unified injection engine that operates at **three insertion points**:

| Insertion point | How |
|----------------|-----|
| **Constructor parameters** | The container selects a constructor and resolves each parameter |
| **Field injection** | After construction, `@Inject`-annotated fields are populated |
| **Module method parameters** | `build*()`, `advise*()`, `contribute*()` method parameters are injected automatically |

---

##### The `@Inject` Annotation (dual-source design)

Freeway ships its own `@Inject` (in `freeway-annotations`) that **coexists with** JSR-330's `javax.inject.Inject`. Both are honored identically by the injection engine, with one addition:

```java
// in com.jujin.freeway.ioc.annotations.Inject
@Target({ FIELD, PARAMETER, CONSTRUCTOR })
@Retention(RUNTIME)
public @interface Inject {
    /** Service id for named injection. Leave empty for type-based injection. */
    String value() default "";
}
```

| Annotation | Behavior |
|------------|----------|
| `@Inject` (no value) | **Type-based** — container finds the single service matching the field/parameter type |
| `@Inject("serviceId")` | **Named** — resolves by service ID directly (no `@Named` needed) |
| `@Inject` + `@Named("id")` | **Named (JSR-330)** — `javax.inject.Named` also works |
| `javax.inject.Inject` | **Type-based** — identical behavior, JSR-330 compatible |

> **Why a separate `@Inject`?** The `value()` attribute on Freeway's `@Inject` eliminates the need for a separate `@Named` annotation in the common case. When writing `@Inject("db")`, one annotation does the work of two.

---

##### Constructor Selection Priority

When instantiating a service, the container picks a constructor by this priority:

1. Constructor annotated with `javax.inject.Inject`
2. Constructor annotated with `com.jujin.freeway.ioc.annotations.Inject`
3. Constructor with **the most parameters** (auto-detected, no annotation needed)
4. Default (no-arg) constructor as fallback

```java
public class MyService {
    // Priority 1 or 2: explicit @Inject
    @Inject
    public MyService(DependencyA a, DependencyB b) { ... }

    // Priority 3: most parameters wins (if no @Inject on any constructor)
    public MyService(DependencyA a) { ... }
    public MyService() { ... }  // would lose to the above
}
```

---

##### Injection Resolution Chain

Every injection request passes through `ObjectInjector` — a chain-of-command that consults `InjectResolver` instances in order. The first non-null result wins:

```
InjectResolver[0]  →  InjectResolver[1]  →  ...  →  fallback: locator.getService()
```

The default resolvers handle:

1. **`@Value("${symbol.key:default}")`** — resolve from config, coerce to target type (supports defaults)
2. **`@Symbol("KEY")`** — resolve from symbol source (e.g., env vars, system properties)
3. **`@Autobuild`** — auto-instantiate the class and inject its dependencies
4. **`ServiceOverride`** — registered programmatically, checked before normal lookup
5. **Fallback** — `ServiceLocator.getService(type)` when `required=true`

```java
// Examples of each resolution strategy
public class DemoService {

    @Inject  @Value("${app.timeout:5000}")
    private int timeout;  // resolved from config, coerced from String to int

    @Inject  @Symbol("DATABASE_URL")
    private String dbUrl; // resolved from symbol source

    @Inject  @Autobuild
    private ComplexHelper helper;  // instantiated + injected by container

    @Inject
    private SimpleService svc;     // type-based: found via ServiceLocator

    @Inject("specificService")
    private NamedService named;    // named injection by service ID
}
```

---

##### Field Injection Mechanics

Field injection runs **after** construction. The engine walks the entire class hierarchy (up to `Object`) and processes all non-static, non-final fields annotated with either `javax.inject.Inject` or Freeway's `@Inject`:

```
For each field:
    1. If Freeway @Inject has non-empty value() → locator.getService(value, fieldType)
    2. If javax.inject.Named present            → locator.getService(named.value, fieldType)
    3. Otherwise → type-based: find resource or locator.getObject(fieldType)
```

---

##### Post-Injection Callback

After all fields are injected, the container invokes methods annotated with `@PostInjection` or `javax.annotation.PostConstruct`:

```java
public class MyService {
    @Inject private Dependency dep;

    @PostInjection
    public void init() {
        // dep is guaranteed to be injected here
        dep.register(this);
    }
}
```

Unlike JSR-250's `@PostConstruct`, Freeway's `@PostInjection` allows **multiple methods** and **method parameters** (which are also injected).

---

##### Module Method Parameter Injection

Parameters of `build*()`, `advise*()`, and `contribute*()` methods are also injected — this is where Freeway modules get their dependencies without explicit `getService()` calls:

```java
public class MyModule {
    // 'storage' is injected by type
    public void buildGreeter(Storage storage) {
        return new GreeterImpl(storage);
    }

    // 'config' is an OrderedConfiguration injected by the container
    @Contribute(RouteRegistry.class)
    public static void addRoutes(OrderedConfiguration<RouteDef> config) {
        config.add("route", new RouteDef(...));
    }

    // '@Value' also works on module method parameters
    public void buildDataSource(@Value("${db.url}") String url) {
        return new DataSource(url);
    }
}
```

---

##### Testing with ServiceOverride

`ServiceOverride` hooks into the injection chain: when a type is overridden, **all** injection points across the container receive the replacement automatically:

```java
Registry registry = Registry.builder()
    .addModule(ProdModule.class)
    .addServiceOverride(Database.class, ctx -> new InMemoryDatabase())
    .build();
```

No mocking library needed for most cases. The override is checked before any service lookup, both for `@Inject` fields and for constructor/parameter resolution.

---

##### Infrastructure Summary

| Component | Role |
|-----------|------|
| `ObjectInjector` | Chain-of-command: iterates `InjectResolver`s, falls back to `getService()` |
| `InjectResolver` | SPI for custom resolution strategies |
| `ServiceOverride` | Type-based override, checked at the front of the chain |
| `GenericsResolver` | Resolves generic type arguments (e.g., `List<String>`) |
| `PropertyAdapter` | Bean property access for injection into JavaBean-style setters |

##### ServiceId — Resolving Multiple Implementations by ID

When two or more implementations are registered under the same type, you need a way to tell the container which one you want. Freeway supports two approaches: **Marker annotations** (covered above) and **ServiceId** — a lightweight string-based identifier.

###### 1. Registering with a ServiceId

**A — During binding (recommended):**

```java
public static void bind(ServiceBinder binder) {
    binder.bind(MultiSvc.class, FastMultiSvcImpl.class).withId("fast");
    binder.bind(MultiSvc.class, SlowMultiSvcImpl.class).withId("slow");
}
```

`withId(id)` sets the service's identity in the registry. If omitted, the default ServiceId is the simple class name of the service interface (e.g. `"MultiSvc"`).

**B — Via `@ServiceId` annotation on the implementation:**

```java
@ServiceId("fast")
public class FastMultiSvcImpl implements MultiSvc { ... }

@ServiceId("slow")
public class SlowMultiSvcImpl implements MultiSvc { ... }
```

The annotation is read when the service class is discovered (e.g. through `@Autobuild` or SPI scanning). It takes priority over the default name-based ServiceId.

---

###### 2. Injecting by ServiceId

**A — `@Inject("id")` (most common):**

```java
public class Consumer {
    @Inject("fast")
    private MultiSvc svc;  // selects FastMultiSvcImpl

    @Inject("slow")
    private MultiSvc anotherSvc;  // selects SlowMultiSvcImpl
}
```

The `value()` attribute of Freeway's `@Inject` doubles as the ServiceId filter. This is equivalent to `@Inject @Named("fast")` but avoids the extra annotation import.

**B — Programmatic lookup:**

```java
MultiSvc svc = registry.getService("fast", MultiSvc.class);
```

---

###### 3. How the resolution works

When `@Inject("fast")` is encountered:

1. The container checks if any registered service matches both the type (`MultiSvc`) **and** the ServiceId (`"fast"`).
2. If found, that implementation is returned directly.
3. If not found, an `IllegalStateException` is thrown — no fallback to a different ServiceId.

This is distinct from type-based fallback: **with a ServiceId, the match is exact on both dimensions.**

---

###### 4. ServiceId vs. Marker — When to use which

| Dimension | ServiceId | Marker Annotation |
|-----------|-----------|-------------------|
| Declaration | `withId("x")` or `@ServiceId("x")` | `@Marker(Red.class)` or `@Red` |
| Injection syntax | `@Inject("x") MultiSvc svc` | `@Inject @Red MultiSvc svc` |
| Extra types needed | No (plain string) | Yes (need to define a `@interface`) |
| Type safety | Runtime (string may be misspelled) | Compile-time (compiler validates annotation) |
| Discoverability | Requires documentation or convention | Annotation appears directly at injection point |
| Refactoring friendliness | String rename won't flag callers | IDE rename refactoring works natively |
| Best for | Quick disambiguation, prototyping, or small projects | Architectural constraints, cross-module contracts, or team-wide conventions |

**Rule of thumb:** Use ServiceId when you just need to tell two things apart. Use Marker when you want to encode business meaning that should survive refactoring.

---

##### @Inject Quick Reference

| Scenario | What to use | Example |
|----------|-------------|---------|
| Single implementation, type-based | `@Inject` (no value) | `@Inject Greeter greeter;` |
| Multiple impls, know the service ID | `@Inject("id")` | `@Inject("fast") Cache cache;` |
| Multiple impls, marker annotation | `@Inject` + `@Marker` | `@Inject @Fast Cache cache;` |
| Config value with default | `@Inject @Value("${key:default}")` | `@Inject @Value("${port:8080}") int port;` |
| Environment / symbol value | `@Inject @Symbol("KEY")` | `@Inject @Symbol("HOME") String home;` |
| Container-managed instantiation | `@Inject @Autobuild` | `@Inject @Autobuild Helper h;` |
| Immutable service (preferred) | Constructor `@Inject` | `@Inject public MyService(Dep d) { ... }` |
| Init after dependencies ready | `@PostInjection` | `void init() { ... }` |
| Testing — swap at container level | `ServiceOverride` | `.addServiceOverride(Db.class, ...)` |

##### When `@Inject` resolves nothing

The injection chain is designed to fail fast and clearly:

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `IllegalStateException: No service found for type X` | Service not registered in any Module | Add `buildX()` to a Module, or check `ModuleProvider` SPI |
| `AmbiguousServiceException` | Multiple services match the same type | Add `@Inject("id")` or a `@Marker` annotation to disambiguate |
| `TypeCoercionException` | `@Value("${...}")` string can't convert to target type | Add a custom `TypeCoercer`, or use `String` as field type |
| Field still `null` after injection | Field is `static` or `final` | Remove `static`/`final`, or use constructor injection instead |

##### Summary: choosing your injection strategy

```
Do you need immutability (final fields)?
  └─ YES → Constructor injection (@Inject on constructor)
  └─ NO  → Is the dependency always available?
             └─ YES → Field injection (@Inject)
             └─ NO  → Is it a configuration value?
                         └─ YES → @Value("${key:default}") or @Symbol("KEY")
                         └─ NO  → Does it need container instantiation?
                                    └─ YES → @Autobuild
                                    └─ NO  → Programmatic lookup via ServiceLocator
```

#### 5. Lifecycle Management — `@Startup`

**Scopes:**

| Scope | Behavior |
|-------|----------|
| `singleton` (default) | Lazily instantiated, cached globally |
| `perthread` | One instance per thread |
| Custom | Via `ServiceLifecycle` interface |

**Startup & Shutdown:**

| Mechanism | When |
|-----------|------|
| `@Startup` | `Registry.performRegistryStartup()` fires |
| `@EagerLoad` | Forces instantiation at startup |
| `RegistryShutdownListener` | Graceful shutdown callback |
| `ThreadCleanupListener` | Per-thread cleanup |

**Service creation pipeline (inside `ModuleImpl`):**

```
ServiceBuilder → ObjectCreator
  → LifecycleWrappedServiceCreator      (scope wrapping)
  → AdvisorStackBuilder                 (advice weaving)
  → RecursiveServiceCreationCheckWrapper(cycle detection)
  → OperationTrackingObjectCreator      (operation tracing)
```

---

### Concept Relationship Map

```
┌───────────────────────────────────────────────────────────────┐
│                        Registry.Builder                       │
│                    (register Module → build Registry)         │
└──────────┬────────────────────────────────────────────────────┘
           │
           ▼
   ┌────────────────────┐
   │      Module        │  ← @ImportModule forms composition tree
   │   (unit of org.)    │  ← ModuleProvider SPI auto-discovery
   └────┬────┬────┬─────┘
        │    │    │
   build│   adv  │contribute
        │    │ise │
   ┌────┴┐ ┌─┴──┐┌─┴──────────────┐
   │Service│ │Advisor││ Configuration     │
   │  Def  │ │  Def  ││(unordered/ordered │
   │  +    │ │  →    ││ /mapped)          │
   │Scope  │ │Method │└───────────────────┘
   │Lifecy.│ │Advice │
   └───┬───┘ └──┬───┘
       │        │
       └────┬───┘
            ▼
    ┌──────────────┐
    │  Automatic   │
    │  Injection   │
    │ (@Inject etc)│
    └──────────────┘
```

---

### Quick Examples

#### Module with SPI discovery

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
        // Executed after all services are ready
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

#### Configuration injection

```java
public class MyService {
    @Symbol("app.server.port")
    private int port;

    @Value("${app.name:Freeway}")
    private String appName;
}
```

Configuration sources (in priority order):
1. Command-line arguments (`--key=value`)
2. `application.yml` / `application.yaml` (classpath)
3. `application.properties` (classpath)
4. Environment variables (prefixed with `FREEWAY_`)

---

## Classic Use Cases

### 1. Three-Layer Architecture (Module as Organizer)

A Module naturally maps to a **vertical slice** of the application — it declares the service, its advisors, and its contributions in one place. No XML, no `@Component`/`@Service` scattered across packages:

```java
public class GreeterModule {

    // ── Service layer ──
    @Build
    Greeter buildGreeter(Storage storage) {
        return new GreeterImpl(storage);
    }

    // ── Cross-cutting: logging (method-level) ──
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

    // ── Plugin extension point: let others add interceptors ──
    @UsesOrderedConfiguration(Interceptor.class)
    public interface GreeterHooks { }

    // ── Lifecycle ──
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

**Why this matters:** Every concern of a feature (service + interceptors + extension points + lifecycle) is co-located in one Module class. Adding a new feature means adding a new Module — not touching five different packages.

---

### 2. Transaction Management via `@Advise`

No `@Transactional` annotation magic. A single Advisor wraps all repository methods with transaction lifecycle:

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
        // same pattern for update(), delete()...
    }
}
```

Because `@Advise` targets a **service interface** and maps by **method name**, you get precise control:
- `adviseAll(...)` — wrap every method on the service
- `adviseMethod("save", ...)` — wrap only `save()`
- Multiple Advisors on the same service = composable pipeline

---

### 3. Plugin Architecture via `@Contribute`

The `@Contribute` mechanism makes any service **extensible from the outside** — the defining trait of a plugin system:

```java
// ── Framework declares a hook point ──
@UsesOrderedConfiguration(Filter.class)
public interface FilterChain { }

// ── Plugin A (in its own JAR) ──
public class LoggingPlugin {
    @Contribute(FilterChain.class)
    public static void addLogging(OrderedConfiguration<Filter> config) {
        config.add("log", ctx -> {
            System.out.println("request: " + ctx.path());
            return ctx.proceed();
        });
    }
}

// ── Plugin B (another JAR) ──
public class MetricsPlugin {
    @Contribute(FilterChain.class)
    public static void addMetrics(OrderedConfiguration<Filter> config) {
        config.add("metrics", ctx -> {
            long t = System.nanoTime();
            var result = ctx.proceed();
            recordLatency(System.nanoTime() - t);
            return result;
        }, "before:log"); // ← positional constraint
    }
}
```

**Key distinction** vs. Spring: Contributions are **static, type-checked, and discoverable** — no runtime component scanning, no reflection. The `OrderedConfiguration` supports `before`/`after` positioning so plugins can declare ordering without fragile numeric priorities.

---

### 4. Testing — Replace Services via `ServiceOverride`

Testing doesn't need a special test framework. Freeway's `ServiceOverride` lets any test Module replace production services by **type**:

```java
// ── Production module ──
public class ProdModule {
    @Build
    Database buildDatabase() {
        return new PostgresDatabase("prod-url");
    }
}

// ── Test module ──
public class TestModule {
    @Build
    Database buildDatabase() {
        return new InMemoryDatabase();
    }
}

// ── Test ──
class MyServiceTest {
    Registry registry;

    @BeforeEach
    void setUp() {
        registry = Registry.builder()
            .addModule(ProdModule.class)
            .addServiceOverride(Database.class, ctx -> new InMemoryDatabase()) // inline override
            .build();
    }

    @Test
    void testBusinessLogic() {
        var service = registry.getService(MyService.class);
        assertDoesNotThrow(() -> service.doWork());
    }
}
```

No mocking library required for most cases. `ServiceOverride` works at the container level — all injection points across all modules automatically receive the test replacement.

---

### 5. Multi-Module Composition via `@ImportModule`

Large applications compose Modules into a tree. The parent Module **explicitly imports** its children — no hidden auto-discovery:

```java
@ImportModule({
    UserModule.class,
    OrderModule.class,
    PaymentModule.class
})
public class AppModule {
    // AppModule aggregates all feature modules
}

// ── Each feature module is independently testable ──
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

The composition tree is **visible and deterministic**:
- No random classpath scanning
- Parent controls which features are wired together
- Child modules don't need to know who imported them
- Different deployments can assemble different trees from the same library modules

---

### 6. Marker Annotations — Disambiguating Same-Interface Services

When multiple services implement the same interface, `@Marker` annotations resolve injection with **type safety** — no magic strings:

```java
// ── Define marker annotations ──
@Marker
@Target(TYPE)
@Retention(RUNTIME)
public @interface Fast { }

@Marker
@Target(TYPE)
@Retention(RUNTIME)
public @interface Reliable { }

// ── Apply to services ──
@Fast
@Build
Cache buildFastCache() { return new LocalCache(); }

@Reliable
@Build
Cache buildReliableCache() { return new RedisCache(); }

// ── Inject by marker ──
public class ReportService {
    @Inject @Fast
    Cache cache;   // << clearly: the fast one

    @Inject @Reliable
    Cache auditCache;  // << clearly: the reliable one
}
```

#### @Marker as a Meta-Capability

`@Marker` is not merely an alternative to `ServiceId` — it is a **meta-capability** that opens Freeway's matching engine to user-defined annotations, making them first-class citizens of the framework.

**Why this matters — four perspectives:**

**1. Annotation as contract**

User-defined annotations (e.g. `@Fast`, `@Audited`, `@PrimaryDC`) participate in the same matching logic (`findServiceDefsMatchingMarkerAndType()`) as framework-builtin ones. No SPI, no interface registration — just a `@interface`. The framework treats your annotations as native concepts.

**2. Cross-layer propagation and composition**

Markers form a hierarchical inheritance chain:

```
Module-level @Marker(Builtin.class)    → default mark for all services in that module
         ↓ inherited
Service-level @Marker(Red.class)        → stacked on top of module defaults
         ↓
Injection point @Inject @Red @Fast Svc → containsAll matching against all stacked markers
```

`@Contribute` and `@Advise` also respect markers — a `@Contribute @Fast void contribute(...)` only affects services marked with `@Fast`. This is **declarative cross-cutting by annotation scope**, not by pointcut expressions.

**3. ServiceId vs. @Marker — capability level**

| Dimension | ServiceId (Tool) | @Marker (Meta-Capability) |
|-----------|------------------|----------------------------|
| Nature | A string identifier in a namespace | An extensible type-system mechanism |
| Extensibility | Fixed (just strings) | User defines any `@interface`, becomes framework-native |
| Participation scope | Service injection only | Injection resolution + contribution matching + advice scoping + module def |
| Composability | None (exact match on one ID) | Multiple markers combine (`containsAll` semantics) |
| Domain semantics | None (plain strings) | Rich semantics (`@ReadOnly`, `@PrimaryDC`, `@Infra`) |
| Refactoring | Silent string rename | IDE-native annotation rename |

**4. What @Marker unlocks for architecture**

| Layer | Effect | Example |
|-------|--------|---------|
| Cross-module isolation | Module-level marker restricts service visibility | `@Marker(Internal.class) public class InfraModule {}` |
| Custom injection dimensions | Users define business-relevant axes | `@Inject @PrimaryDC DataSource ds` |
| Scoped cross-cutting | Contribution/advice automatically filtered by marker | `@Contribute @Fast void config(ServiceBundle b)` |
| Architectural contract | Team-defined annotations become enforceable constraints | `@ReadOnly @Audited` only matched by read-only audit services |
| Declarative composition | Multiple markers stacked for precise matching | `@Inject @Red @Audited Service svc` |

**Rule of thumb:** `ServiceId` is the "quick lane" for same-interface disambiguation; `@Marker` is the **extensibility lever** — use it when you want your own annotations to become part of Freeway's resolution semantics.

---

### 7. Multi-Tenant perThread Scopes

`perthread` scope creates isolated service instances per-thread — ideal for multi-tenant or request-scoped state without a container-managed request lifecycle:

```java
@Scope("perthread")
@Build
CurrentUser buildCurrentUser() {
    return new CurrentUser();
}

// ── Each thread gets its own instance ──
class RequestHandler {
    @Inject
    CurrentUser currentUser;

    void handle(String userId) {
        currentUser.setId(userId);
        // ... all downstream code sees this thread's user
    }
}
```

And within the `@Startup` phase, you can pre-configure:

```java
@Startup
void warmup(Registry registry) {
    registry.parallelExecute(() -> {
        // Pre-warm per-thread services for worker threads
    });
}
```

---

## Design Summary

| Pattern | Freeway Approach | vs. Spring |
|---------|-----------------|------------|
| Service definition | Static `build*()` methods in Module | `@Component` classpath scanning |
| AOP | `@Advise` + `advisor.adviseMethod(...)` | `@Aspect` + pointcut expressions |
| Extension points | `@Contribute` to `OrderedConfiguration` | `@Autowired List<X>` + `@Order` |
| Module composition | Explicit `@ImportModule` tree | `@Import` + auto-configuration |
| Test isolation | `ServiceOverride` at container level | `@MockBean` / `@TestConfiguration` |
| Qualifiers | Type-safe `@Marker` annotations | `@Qualifier("string")` |
| Scopes | Per-scope via `ServiceLifecycle` interface | `@Scope("prototype")` etc. |

---

### Web — Handler-only architecture

Pure Handler pattern — no `@Controller`, no `@RequestMapping`, no reflection-based routing:

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

`FreewayContext` is server-agnostic — swap the HTTP engine by changing one Maven dependency.

### JSON

Zero-dependency JSON library covering the 95% use case:

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

### DB — Database Access Layer

Zero-dependency database access with a built-in connection pool. The `Database` object IS the pool — no separate `DataSource` + `HikariCP` + `JdbcTemplate` needed.

**Quick start:**

```java
// Standalone (no IoC required)
Database db = Database.builder()
    .url("jdbc:postgresql://localhost:5432/mydb")
    .username("app").password("secret")
    .build();

// Query — auto-map to Record
record User(long id, String name, String email) {}

List<User> users = db.sql("SELECT id, name, email FROM users WHERE status = ?", "active")
    .list(User.class);

// Named params — #name style (recommended), order-independent
db.sql("INSERT INTO users (id, name, email) VALUES (#id, #name, #email)")
  .param("email", "alice@example.com")
  .param("name", "Alice")
  .param("id", 100001)
  .execute();

// Transactions — lambda boundary = transaction boundary
db.transaction(tx -> {
    tx.sql("UPDATE accounts SET balance = balance - :amt WHERE id = :id")
      .param("amt", 100).param("id", fromId).execute();
    tx.sql("UPDATE accounts SET balance = balance + :amt WHERE id = :id")
      .param("amt", 100).param("id", toId).execute();
});
// normal return → commit, exception → rollback
```

**IoC integration:**

```java
// DbModule auto-configures — just write the config
// application.yml:
//   freeway.db.url: jdbc:postgresql://...
//   freeway.db.username: app
//   freeway.db.password: secret

@Primary Database db;  // injects the default database
```

**Multiple datasources:**

```java
// Marker-based — compile-time type safety
@Primary Database primary;
@ReadOnly Database replica;

// Config prefix-based — runtime flexibility
// freeway.db.datasources: replica, analytics
// freeway.db.datasources.analytics.url: jdbc:clickhouse://...
@Inject Databases databases;
Database analytics = databases.get("analytics");
```

**Migrations:**

```
src/main/resources/db/migration/
  V001__create_users.sql
  V002__add_email_column.sql
```

Auto-executed on startup in order, tracked in `_migrations` table. Idempotent and safe.

**Custom RowMapper:**

```java
@Contribute(RowMapperOverrides.class)
public static void myMappers(MappedConfiguration<Class<?>, RowMapper<?>> config) {
    config.add(MyJsonType.class, (rs, rowNum) ->
        MyJsonType.parse(rs.getString("payload")));
}
```

**Key features:**

| Feature | Description |
|---------|-------------|
| Connection pool | Semaphore + ConcurrentLinkedDeque, zero deps |
| Query | `#name` / `:name` named params + `?` positional |
| Mapping | Record (canonical constructor) and JavaBean (MethodHandle setters) |
| Transactions | Lambda-style + try-with-resources manual |
| Type coercion | Optional `TypeCoercer`, fallback to built-in basicCoerce |
| Migrations | ClassPathScanner scans `db/migration/`, sequential execution |
| Multi-datasource | `@Primary`/`@ReadOnly` markers + `Databases` hub |

---

## Project Status

**Active development.** Key milestones:

- IoC container — full service lifecycle, contributions, advisors
- JSON — zero dependencies
- DB — built-in connection pool, named params, Record/Bean mapping, migrations
- Web — Handler-based routing, path variables, filter chains, exception mappers
- Boot — YAML/properties/env/CLI config, graceful shutdown

---

*Built for JDK 25+. No Spring. No magic.*
