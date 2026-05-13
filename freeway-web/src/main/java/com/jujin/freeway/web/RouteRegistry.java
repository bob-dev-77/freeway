package com.jujin.freeway.web;

import com.jujin.freeway.ioc.annotations.UsesOrderedConfiguration;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe route registry. Supports path patterns with {param} placeholders,
 * e.g. /api/users/{id}/posts/{postId}
 *
 * <p>
 * Routes are contributed by user modules via
 * {@code @Contribute(RouteRegistry.class)}
 * </p>
 */
@UsesOrderedConfiguration(RouteDef.class)
public class RouteRegistry {

    private final ConcurrentHashMap<String, List<Route>> routes = new ConcurrentHashMap<>();
    private final Logger logger;

    /** Called by IoC: receives all contributed RouteDefs. */
    public RouteRegistry(final List<RouteDef> routeDefs, Logger logger) {
        this.logger = logger;
        for (RouteDef def : routeDefs) {
            String key = def.method().toUpperCase();
            routes.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>())
                .add(new Route(def.path(), def.handler()));
        }
    }

    /** Total routes across all HTTP methods. */
    public int routeCount() {
        return routes.values().stream().mapToInt(List::size).sum();
    }

    /** Register a handler for given HTTP method and path pattern. */
    public void addRoute(String method, String path, RouteHandler handler) {
        String key = method.toUpperCase();
        List<Route> list = routes.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        synchronized (list) {
            for (Route existing : list) {
                if (existing.pattern.equals(path)) {
                    logger.warn("Duplicate route: {} {} — second handler will be shadowed", key, path);
                    return;
                }
            }
            list.add(new Route(path, handler));
        }
    }

    /** Match a request path against registered routes. Returns null if no match. */
    public RouteMatch match(String method, String path) {
        String key = method.toUpperCase();
        List<Route> candidates = routes.get(key);
        if (candidates == null)
            return null;
        for (Route route : candidates) {
            Map<String, String> vars = route.match(path);
            if (vars != null) {
                return new RouteMatch(route.handler, vars);
            }
        }
        return null;
    }

    // ---- Internal types ----

    static class Route {
        final String pattern;
        final String[] segments; // static segments (null = param slot)
        final String[] paramNames; // parameter names (non-null entries are params)
        final boolean wildcard; // true if last segment is {name:.*}
        final RouteHandler handler;

        Route(String pattern, RouteHandler handler) {
            this.pattern = pattern;
            this.handler = handler;
            String[] raw = splitPath(pattern);
            this.segments = new String[raw.length];
            boolean wc = false;
            List<String> params = new ArrayList<>();
            for (int i = 0; i < raw.length; i++) {
                String seg = raw[i];
                if (seg.startsWith("{") && seg.endsWith("}")) {
                    String inner = seg.substring(1, seg.length() - 1);
                    int colon = inner.indexOf(':');
                    if (colon > 0) {
                        String name = inner.substring(0, colon);
                        String regex = inner.substring(colon + 1);
                        params.add(name);
                        if (".*".equals(regex) && i == raw.length - 1) {
                            wc = true;
                        }
                    } else {
                        params.add(inner);
                    }
                    segments[i] = null;
                } else {
                    segments[i] = seg;
                    params.add(null);
                }
            }
            this.wildcard = wc;
            this.paramNames = params.toArray(new String[0]);
        }

        /** Try to match a concrete path. Returns param map or null. */
        Map<String, String> match(String path) {
            String[] input = splitPath(path);
            if (wildcard) {
                if (input.length < segments.length)
                    return null;
            } else if (input.length != segments.length) {
                return null;
            }
            Map<String, String> vars = new LinkedHashMap<>();
            for (int i = 0; i < segments.length; i++) {
                if (segments[i] == null) {
                    if (wildcard && i == segments.length - 1) {
                        // Greedy wildcard: join all remaining segments
                        String remainder = String.join("/",
                            java.util.Arrays.copyOfRange(input, i, input.length));
                        if (remainder.isEmpty())
                            return null;
                        if (containsPathTraversal(remainder))
                            return null;
                        vars.put(paramNames[i], remainder);
                    } else {
                        if (input[i].isEmpty())
                            return null;
                        if ("..".equals(input[i]))
                            return null;
                        vars.put(paramNames[i], input[i]);
                    }
                } else if (!segments[i].equals(input[i])) {
                    return null;
                }
            }
            return vars;
        }

        private static String[] splitPath(String path) {
            if (path == null || path.isEmpty() || path.equals("/"))
                return new String[0];
            String normalized = path.startsWith("/") ? path.substring(1) : path;
            if (normalized.endsWith("/"))
                normalized = normalized.substring(0, normalized.length() - 1);
            if (normalized.isEmpty())
                return new String[0];
            return normalized.split("/");
        }

        private static boolean containsPathTraversal(String path) {
            for (String seg : path.split("/")) {
                if ("..".equals(seg))
                    return true;
            }
            return false;
        }
    }

    /** Result of a route match. */
    public record RouteMatch(RouteHandler handler, Map<String, String> pathVariables) {}
}
