# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

No Maven wrapper — requires a local Maven installation. All commands from repo root.

```bash
mvn clean compile              # Build all modules
mvn test                       # Run all tests
mvn test -Dtest=ClassName      # Run a single test class (any module)
mvn -pl freeway-ioc test -Dtest=com.jujin.freeway.test.inject.InjectTest  # Module-specific test
mvn install -DskipTests        # Install all modules to local ~/.m2 (common for local dev)
```

There is no formatting/lint step — Spotless was removed from the build.

## Architecture

Freeway is a lightweight IoC/DI + Boot + Web framework inspired by Apache Tapestry 5 IoC. It targets **JDK 25** and has zero required external dependencies beyond SLF4J and javax.inject.

### Module dependency order (bottom → top)

```
freeway-annotations  →  freeway-commons  →  freeway-ioc  →  freeway-boot  →  freeway-boot-starter
                                                            →  freeway-web
                                                            →  freeway-db
```

`freeway-web` and `freeway-db` are peer modules — both depend on `freeway-ioc` (and transitively on
annotations/commons), but neither depends on the other.

### IoC container (the core)

The IoC container is modeled after Tapestry 5 IoC with these key concepts:

- **Module classes** — plain Java classes with static methods. Four method kinds:
  1. `bind(ServiceBinder)` — binds service interfaces to implementations
  2. Builder methods (e.g. `public MyService build(Dep1 d1, Dep2 d2)`) — construct services via auto-wired parameters. Method naming conventions (`build*`, `contribute*`) are equivalent to annotations
  3. `@Contribute(SomeService.class)` — contribute to ordered/mapped service configurations
  4. `@Startup` — run after all services are ready

- **Service auto-discovery** — modules are registered via JDK `ServiceLoader`:
  - Implement `ModuleProvider` (returns `Class<?>[]`) — declare one or more module classes, compose sub-modules via `@ImportModule`
  - Declare in `META-INF/services/com.jujin.freeway.ioc.ModuleProvider`
  - Call `registryBuilder.autoDiscover()` or use `FreewayApplication.run()`

- **`Registry.Builder`** (`freeway-ioc/src/main/java/.../ioc/Registry.java`) assembles the container. The built-in module `FreewayIOCModule` (`freeway-ioc/src/main/java/.../ioc/FreewayIOCModule.java`) wires up all core services (TypeCoercer, SymbolSource, Injector, logging, threading, etc.). `Registry.startAndBuild(Class...)` builds and starts a Registry; `Registry.spiAndBuild(Class...)` additionally runs `autoDiscover()` for SPI modules.

- **Configuration pipeline** (defined in `FreewayApplication.loadConfig()`):
  Priority: CLI args `--key=value` (100) → `application.yml` (300) → `application.properties` (400) → `FREEWAY_` env vars (500). The YAML parser is custom (no SnakeYAML dependency).

- **`Registry.spiAndBuild(Class...)`** = `startAndBuild(Class...)` + `autoDiscover()` — use when modules come from SPI auto-discovery.

- **Scopes**: singleton (default), perthread. Proxiable services (those with interfaces) can use custom scopes via `ServiceLifecycleSource`. Thread pools and the web server use virtual threads by default.

- **No mocking library** — tests use `Registry.Builder.addServiceOverride(Class, ServiceOverride)` to swap real implementations at the container level rather than mocking at the test level.

### Web layer

Handler-only architecture — no `@Controller` or `@RequestMapping`:

- Routes contributed via `@Contribute(RouteRegistry.class)` as `RouteDef` objects
- Filters via `@Contribute(HttpFilterChain.class)` as `HttpFilter` lambdas
- Exception mappers via `@Contribute(ExceptionMapper.class)`
- `FreewayContext` is server-agnostic; current impl uses JDK `HttpServer` + robaho httpserver (high-performance impl
  with HTTP/2 + WebSocket support)
- `WebModule` (`freeway-web/src/main/java/.../web/WebModule.java`) starts the server on `@Startup`

### DB layer

Zero-dependency database access (`freeway-db`). `Database` is the central object — it IS the connection pool (
semaphore + ConcurrentLinkedDeque):

- **Query styles**: `?` positional, `:name` / `#name` named params. `Database.sql(...)` returns a `Query` builder.
- **Row mapping**: Record types (canonical constructor) and JavaBean (MethodHandle setters). Custom mappers via
  `@Contribute(RowMapperOverrides.class)`.
- **Transactions**: Lambda-style (`database.transaction(tx -> { ... })`) — normal return commits, exception rolls back.
  Also try-with-resources manual mode via `tx.sql(...)`.
- **Migrations**: Classpath-scanned from `db/migration/`, executed sequentially on startup, tracked in `_migrations`
  table.
- **Multi-datasource**: `@Primary`/`@ReadOnly` marker annotations on injection points; `Databases` hub for runtime
  lookup by name. Configurable via `freeway.db.datasources.*` config keys.
- `DbModule` (auto-discovered via `ModuleProvider` SPI) wires everything — just add the dependency and provide
  `freeway.db.url`/`username`/`password` in config.

### JSON library

`freeway-commons` includes a zero-dependency JSON library (under `com.jujin.freeway.commons.json`). Core types: `JSONObject`, `JSONArray`, `JSONTokener`. `JSONUtils` provides `toJson()`/`fromJson()` helpers.

## Key APIs for testing

```java
// Build a bare IoC container from a module class
Registry registry = Registry.startAndBuild(TestModule.class);
TestService svc = registry.getService(TestService.class);

// Build a full boot app (includes config loading + auto-discovery)
FreewayApp app = FreewayApplication.run(MyApp.class, args);
Registry registry = app.getRegistry();

// Always shut down
registry.shutdown();  // or app.shutdown();
```

## Code conventions

- **No reflection for service binding** — all wiring is explicit via static module methods
- Internal implementation classes live under `.internal.*` packages
- `*Impl` suffix for implementation classes, `*Def` for service definition types
- `*Def2`/`*Def3` variants exist for progressive refinement (similar to Tapestry's pattern)
- Source count: ~350 files, ~36k lines — each module is intentionally small
- `@SuppressWarnings("all")` is used on many internal classes
