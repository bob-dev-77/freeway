package com.jujin.freeway.web;

/**
 * A route definition — mapping of HTTP method + path pattern to a handler
 * function.
 *
 * <p>
 * Registered via {@code @Contribute(RouteRegistry.class)} by user modules.
 *
 * @param method
 *            HTTP method (GET, POST, PUT, DELETE, etc.)
 * @param path
 *            Path pattern supporting {@code {param}} placeholders, e.g.
 *            {@code /api/users/{id}}
 * @param handler
 *            Handler function that processes the request
 */
public record RouteDef(String method, String path, RouteHandler handler) {
    public static final String HTTP_GET = "GET";
    public static final String HTTP_POST = "POST";
    public static final String HTTP_PUT = "PUT";
    public static final String HTTP_DELETE = "DELETE";
    public static final String HTTP_PATCH = "PATCH";
    public static final String HTTP_HEAD = "HEAD";
    public static final String HTTP_OPTIONS = "OPTIONS";

}
