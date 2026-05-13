package com.jujin.freeway.web;

/**
 * Request filter executed before the route handler. Call
 * {@code next.handle(ctx)} to pass control to the next filter or terminal
 * handler.
 */
@FunctionalInterface
public interface HttpFilter {

    /**
     * Process the request. Call {@code next.handle(ctx)} to continue the
     * pipeline, or omit the call to short-circuit (e.g. for auth rejection).
     */
    void doFilter(HttpContext ctx, RouteHandler next) throws Exception;
}
