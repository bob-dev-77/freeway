package com.jujin.freeway.web;

import com.jujin.freeway.ioc.RegistryShutdownHub;
import com.jujin.freeway.ioc.ServiceBinder;
import com.jujin.freeway.ioc.ServiceLocator;
import com.jujin.freeway.ioc.annotations.*;
import com.jujin.freeway.ioc.config.MappedConfiguration;
import com.jujin.freeway.ioc.config.OrderedConfiguration;
import com.jujin.freeway.ioc.lifecycle.PerThreadManager;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.slf4j.Logger;

/**
 * Freeway Web IoC module — starts the built-in HTTP server on {@code @Startup}.
 *
 * <p>
 * Server is based on JDK {@code com.sun.net.httpserver.HttpServer} (with robaho
 * httpserver on the classpath for enhanced performance and HTTP/2 support).
 *
 * <p>
 * User modules contribute routes via {@code @Contribute(RouteRegistry.class)}:
 * </p>
 *
 * <pre>{@code
 * @Contribute(RouteRegistry.class)
 * public static void routes(OrderedConfiguration<RouteDef> config) {
 *     config.add("hello", new RouteDef("GET", "/api/hello", ctx -> ctx.send(200, "Hello!")));
 * }
 * }</pre>
 *
 * <p>
 * Filters and exception mappers are contributed similarly:
 * </p>
 *
 * <pre>{@code
 * @Contribute(HttpFilterChain.class)
 * public static void filters(OrderedConfiguration<HttpFilter> config) {
 *     config.add("log", (ctx, chain) -> {
 *         System.out.println(ctx.method() + " " + ctx.path());
 *         chain.handle(ctx);
 *     });
 * }
 * }</pre>
 *
 * <p>
 * To swap the HTTP engine, exclude robaho and add a different
 * {@code HttpServer} implementation on the classpath.
 * </p>
 */
@Marker(Builtin.class)
public class WebModule {

    private static final String PREFIX = "freeway.server";

    // ── Bind services ────────────────────────────────────────────

    public static void bind(ServiceBinder binder) {
        binder.bind(RouteRegistry.class, RouteRegistry.class);
        binder.bind(JsonCodec.class, DefaultJsonCodec.class);
        binder.bind(CorsFilter.class, CorsFilter.class);
        binder.bind(HttpFilterChain.class, HttpFilterChain.class);
        binder.bind(ExceptionMapperChain.class, ExceptionMapperChain.class);
        binder
            .bind(RequestContext.class, DefaultRequestContext.class)
            .scope("perthread");
    }

    // ── Default configuration ──────────────────────────────────────

    @Contribute(com.jujin.freeway.ioc.symbol.SymbolProvider.class)
    @FactoryDefaults
    public static void contributeDefaults(
        MappedConfiguration<String, Object> config
    ) {
        config.add(PREFIX + ".port", 8080);
        config.add(PREFIX + ".host", "0.0.0.0");
        config.add(PREFIX + ".backlog", 0);
        config.add(PREFIX + ".shutdown-grace-seconds", 2);
        // CORS defaults
        config.add("cors.enabled", true);
        config.add("cors.allowed-origins", "*");
        config.add(
            "cors.allowed-methods",
            "GET, POST, PUT, DELETE, PATCH, OPTIONS"
        );
        config.add("cors.allowed-headers", "Content-Type, Authorization");
        config.add("cors.exposed-headers", "");
        config.add("cors.max-age", "3600");
        config.add("cors.allow-credentials", false);
    }

    // ── Auto-contribute built-in filters ───────────────────────────

    @Contribute(HttpFilterChain.class)
    public static void contributeCorsFilter(
        OrderedConfiguration<HttpFilter> config,
        CorsFilter corsFilter
    ) {
        config.add("cors", corsFilter, "before:*");
    }

    // ── Auto-contribute built-in exception mappers ───────────────────

    @Contribute(ExceptionMapperChain.class)
    public static void contributeExceptionMappers(
        OrderedConfiguration<ExceptionMapper> config
    ) {
        // Handle request body too large - return 413 Payload Too Large
        config.add(
            "body-size",
            (ctx, ex) -> {
                if (ex instanceof RequestBodyTooLargeException) {
                    var bodyEx = (RequestBodyTooLargeException) ex;
                    ctx.status(413);
                    try {
                        ctx.sendJson(
                            413,
                            Map.of(
                                "error",
                                "Payload Too Large",
                                "message",
                                ex.getMessage(),
                                "maxSize",
                                bodyEx.getMaxSize()
                            )
                        );
                    } catch (Exception e) {
                        // Fallback to plain text if JSON fails
                        try {
                            ctx.send(
                                413,
                                "Payload Too Large: " + ex.getMessage()
                            );
                        } catch (Exception ex2) {
                            // Log but don't throw - we're in an error handler
                            java.util.logging.Logger.getLogger(
                                WebModule.class.getName()
                            ).severe(
                                "Failed to send 413 response: " +
                                    ex2.getMessage()
                            );
                        }
                    }
                    return true;
                }
                return false;
            },
            "first"
        );
    }

    // ── Server startup ─────────────────────────────────────────────

    @Startup
    public static void startWebServer(
        RouteRegistry routeRegistry,
        JsonCodec jsonCodec,
        HttpFilterChain filterChain,
        ExceptionMapperChain exceptionMapperChain,
        RegistryShutdownHub shutdownHub,
        PerThreadManager perthreadManager,
        ServiceLocator serviceLocator,
        Logger logger,
        @Symbol(PREFIX + ".port") int port,
        @Symbol(PREFIX + ".host") String host,
        @Symbol(PREFIX + ".backlog") int backlog,
        @Symbol(PREFIX + ".shutdown-grace-seconds") int shutdownGraceSeconds
    ) throws Exception {
        // Add default health endpoint before logging count (users can override)
        addHealthRoute(routeRegistry, logger);

        logger.info(
            "Freeway Web: registered {} routes",
            routeRegistry.routeCount()
        );
        int filterCount = filterChain.filters().size();
        if (filterCount > 0) {
            logger.info("Freeway Web: {} filters registered", filterCount);
        }

        // 1. Create executor and server
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var server = HttpServer.create(
            new InetSocketAddress(host, port),
            backlog
        );
        server.setExecutor(executor);

        // 2. Register a single catch-all context that delegates to RouteRegistry
        server.createContext("/", exchange -> {
            perthreadManager.run(() -> {
                var ctx = new JdkHttpContext(exchange, jsonCodec, logger);
                // Trigger perthread RequestContext creation for this request
                serviceLocator.getService(RequestContext.class);
                try {
                    var match = routeRegistry.match(ctx.method(), ctx.path());
                    if (match != null) {
                        ctx.pathVariables(match.pathVariables());
                        buildChain(
                            match.handler(),
                            filterChain.filters()
                        ).handle(ctx);
                    } else {
                        ctx.send(404, "Not Found");
                    }
                } catch (Exception e) {
                    handleException(
                        ctx,
                        e,
                        exceptionMapperChain.mappers(),
                        logger
                    );
                }
            });
        });

        // 3. Start server and register shutdown hooks
        server.start();
        int actualPort = server.getAddress().getPort();
        int grace = shutdownGraceSeconds > 0 ? shutdownGraceSeconds : 2;

        // Register shutdown listeners in reverse order of dependency
        shutdownHub.addRegistryShutdownListener(
            (Runnable) () -> {
                logger.info(
                    "Freeway Web: stopping server on port {}...",
                    actualPort
                );
                server.stop(grace);
                logger.info("Freeway Web: server stopped");
            }
        );

        shutdownHub.addRegistryShutdownListener(
            (Runnable) () -> {
                logger.info("Freeway Web: shutting down executor...");
                executor.shutdown();
                logger.info("Freeway Web: executor shut down");
            }
        );

        logger.info("Freeway Web: started on {}:{}", host, actualPort);
    }

    private static void addHealthRoute(
        RouteRegistry routeRegistry,
        Logger logger
    ) {
        routeRegistry.addRoute("GET", "/health", ctx -> {
            try {
                ctx.sendJson(200, Map.of("status", "UP"));
            } catch (Exception e) {
                // Fallback to plain text if JSON fails
                logger.warn(
                    "Health check JSON serialization failed, using fallback",
                    e
                );
                try {
                    ctx.send(200, "UP");
                } catch (Exception ex) {
                    logger.error("Health check completely failed", ex);
                }
            }
        });
    }

    // ---- Internal helpers ----

    /** Build a filter chain that terminates with the route handler. */
    static RouteHandler buildChain(
        RouteHandler handler,
        List<HttpFilter> filters
    ) {
        if (filters == null || filters.isEmpty()) {
            return handler;
        }
        RouteHandler chain = handler;
        for (int i = filters.size() - 1; i >= 0; i--) {
            HttpFilter filter = filters.get(i);
            RouteHandler next = chain;
            chain = ctx -> filter.doFilter(ctx, next);
        }
        return chain;
    }

    static void handleException(
        HttpContext ctx,
        Exception e,
        List<ExceptionMapper> mappers,
        Logger logger
    ) {
        // Try each mapper in order, but isolate mapper failures
        for (ExceptionMapper mapper : mappers) {
            try {
                if (mapper.handle(ctx, e)) {
                    return; // Mapper handled the exception successfully
                }
            } catch (Exception mapperEx) {
                // Log mapper failure and continue to next mapper
                logger.error(
                    "Exception mapper {} failed while handling: {}",
                    mapper.getClass().getSimpleName(),
                    e.getMessage(),
                    mapperEx
                );
            }
        }

        // All mappers failed or declined - use fallback response
        logger.error(
            "Unhandled exception for {} {}",
            ctx.method(),
            ctx.path(),
            e
        );
        try {
            ctx.send(500, "Internal Server Error");
        } catch (Exception ex) {
            logger.error("Failed to send error response", ex);
        }
    }
}
