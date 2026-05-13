package com.jujin.freeway.web;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RouteRegistry} path matching logic.
 */
class RouteRegistryTest {

    private static final Logger logger = LoggerFactory.getLogger(RouteRegistryTest.class);

    private final RouteRegistry registry = new RouteRegistry(List.of(
        new RouteDef("GET", "/api/hello", ctx -> {}),
        new RouteDef("GET", "/api/users/{id}", ctx -> {}),
        new RouteDef("GET", "/api/users/{id}/posts/{postId}", ctx -> {}),
        new RouteDef("POST", "/api/data", ctx -> {}),
        new RouteDef("GET", "/api/search", ctx -> {}),
        new RouteDef("GET", "/api/static/{path:.*}", ctx -> {})), logger);

    @Test
    void routeCount() {
        assertEquals(6, registry.routeCount());
    }

    @Test
    void emptyRegistry() {
        var empty = new RouteRegistry(List.of(), logger);
        assertEquals(0, empty.routeCount());
        assertNull(empty.match("GET", "/any"));
    }

    @Test
    void exactPathMatch() {
        var result = registry.match("GET", "/api/hello");
        assertNotNull(result);
        assertTrue(result.pathVariables().isEmpty());
    }

    @Test
    void exactPathMatchDifferentMethod() {
        assertNull(registry.match("POST", "/api/hello"));
    }

    @Test
    void pathVariableSingle() {
        var result = registry.match("GET", "/api/users/42");
        assertNotNull(result);
        assertEquals("42", result.pathVariables().get("id"));
        assertEquals(1, result.pathVariables().size());
    }

    @Test
    void pathVariableMultiple() {
        var result = registry.match("GET", "/api/users/99/posts/hello-world");
        assertNotNull(result);
        assertEquals("99", result.pathVariables().get("id"));
        assertEquals("hello-world", result.pathVariables().get("postId"));
    }

    @Test
    void pathVariableNotMatched() {
        assertNull(registry.match("GET", "/api/users"));
    }

    @Test
    void caseInsensitiveMethod() {
        var result = registry.match("get", "/api/hello");
        assertNotNull(result);
    }

    @Test
    void notFoundReturnsNull() {
        assertNull(registry.match("GET", "/api/nonexistent"));
        assertNull(registry.match("PUT", "/api/hello"));
    }

    @Test
    void pathWithTrailingSlashIsNormalized() {
        var result = registry.match("GET", "/api/hello/");
        assertNotNull(result);
    }

    // ── Wildcard tests ──────────────────────────────────────

    @Test
    void wildcardMatchesSingleSegment() {
        var result = registry.match("GET", "/api/static/index.html");
        assertNotNull(result);
        assertEquals("index.html", result.pathVariables().get("path"));
    }

    @Test
    void wildcardMatchesMultipleSegments() {
        var result = registry.match("GET", "/api/static/css/main/style.css");
        assertNotNull(result);
        assertEquals("css/main/style.css", result.pathVariables().get("path"));
    }

    @Test
    void wildcardRequiresAtLeastOneSegment() {
        assertNull(registry.match("GET", "/api/static"));
        assertNull(registry.match("GET", "/api/static/"));
    }

    @Test
    void wildcardDoesNotMatchShorterPath() {
        assertNull(registry.match("GET", "/api"));
    }

    // ── RouteDef constants ──────────────────────────────────

    @Test
    void httpMethodConstants() {
        assertEquals("GET", RouteDef.HTTP_GET);
        assertEquals("POST", RouteDef.HTTP_POST);
        assertEquals("PUT", RouteDef.HTTP_PUT);
        assertEquals("DELETE", RouteDef.HTTP_DELETE);
        assertEquals("PATCH", RouteDef.HTTP_PATCH);
        assertEquals("HEAD", RouteDef.HTTP_HEAD);
        assertEquals("OPTIONS", RouteDef.HTTP_OPTIONS);
    }

    // ── addRoute ────────────────────────────────────────────

    @Test
    void addRouteAfterConstruction() {
        registry.addRoute("DELETE", "/api/resource/{id}", ctx -> {});
        assertEquals(7, registry.routeCount());
        var result = registry.match("DELETE", "/api/resource/abc");
        assertNotNull(result);
        assertEquals("abc", result.pathVariables().get("id"));
    }

    @Test
    void concurrentAddRouteDoesNotBreakMatching() throws InterruptedException {
        Thread t1 = new Thread(() -> registry.addRoute("GET", "/api/t1", ctx -> {}));
        Thread t2 = new Thread(() -> registry.addRoute("GET", "/api/t2", ctx -> {}));
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertNotNull(registry.match("GET", "/api/t1"));
        assertNotNull(registry.match("GET", "/api/t2"));
        assertEquals(8, registry.routeCount());
    }

    // ── Path traversal prevention ────────────────────────────

    @Test
    void wildcardRejectsParentDirectory() {
        assertNull(registry.match("GET", "/api/static/../../etc/passwd"));
    }

    @Test
    void wildcardRejectsParentDirectoryMidPath() {
        assertNull(registry.match("GET", "/api/static/css/../secret/config"));
    }

    @Test
    void wildcardRejectsDotDotSegment() {
        assertNull(registry.match("GET", "/api/static/.."));
    }

    @Test
    void pathVariableRejectsParentDirectory() {
        var r = new RouteRegistry(List.of(
            new RouteDef("GET", "/files/{name}", ctx -> {})), logger);
        assertNull(r.match("GET", "/files/.."));
    }

    // ── Duplicate route prevention ───────────────────────────

    @Test
    void addRouteDuplicateIsSilentlyIgnored() {
        var r = new RouteRegistry(List.of(
            new RouteDef("GET", "/only", ctx -> {})), logger);
        assertEquals(1, r.routeCount());
        r.addRoute("GET", "/only", ctx -> {});
        assertEquals(1, r.routeCount()); // duplicate not added
    }

    // ── RouteMatch record ───────────────────────────────────

    @Test
    void routeMatchRecord() {
        var result = registry.match("GET", "/api/users/42");
        assertNotNull(result);
        assertNotNull(result.handler());
        assertEquals(Map.of("id", "42"), result.pathVariables());
    }
}
