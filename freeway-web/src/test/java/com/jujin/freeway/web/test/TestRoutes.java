package com.jujin.freeway.web.test;

import com.jujin.freeway.ioc.annotations.Contribute;
import com.jujin.freeway.ioc.config.OrderedConfiguration;
import com.jujin.freeway.web.RouteDef;
import com.jujin.freeway.web.RouteRegistry;

/**
 * Test routes using Handler mode.
 */
public class TestRoutes {

    @Contribute(RouteRegistry.class)
    public static void routes(OrderedConfiguration<RouteDef> routes) {
        routes.add("hello", new RouteDef("GET", "/api/hello", ctx -> ctx.send(200, "Hello, Freeway!")));

        routes.add("echo", new RouteDef("GET", "/api/echo", ctx -> {
            String msg = ctx.queryParam("msg");
            ctx.send(200, "Echo: " + (msg != null ? msg : "none"));
        }));

        routes.add("getUser",
            new RouteDef("GET", "/api/users/{id}", ctx -> ctx.send(200, "User " + ctx.pathVar("id"))));

        routes.add("postData", new RouteDef("POST", "/api/data", ctx -> {
            var body = ctx.bodyAsJson(java.util.Map.class);
            ctx.sendJson(200, java.util.Map.of("received", body, "status", "ok"));
        }));
    }
}
