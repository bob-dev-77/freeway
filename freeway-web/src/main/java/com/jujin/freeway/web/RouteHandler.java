package com.jujin.freeway.web;

/**
 * Handler functional interface. Takes a server-agnostic context.
 */
@FunctionalInterface
public interface RouteHandler {
    void handle(HttpContext ctx) throws Exception;
}
