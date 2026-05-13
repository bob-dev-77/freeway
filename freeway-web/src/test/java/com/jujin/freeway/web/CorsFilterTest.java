package com.jujin.freeway.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CorsFilterTest {

    @Test
    void allowAllOrigins() {
        var filter = CorsFilter.builder().allowAllOrigins().build();
        assertNotNull(filter);
    }

    @Test
    void builderDefaults() {
        var f = CorsFilter.builder().build();
        assertNotNull(f);
    }

    @Test
    void allowAllOriginsWithCredentialsThrows() {
        assertThrows(IllegalStateException.class, () ->
            CorsFilter.builder().allowAllOrigins().allowCredentials(true).build());
    }

    @Test
    void specificOriginWithCredentialsDoesNotThrow() {
        var f = CorsFilter.builder()
            .allowedOrigins("https://example.com")
            .allowCredentials(true)
            .build();
        assertNotNull(f);
    }

    @Test
    void toFilterSetsCorsHeadersOnNonOptionsRequest() throws Exception {
        var filter = CorsFilter.builder().allowAllOrigins().build();
        var ctx = new StubHttpContext();
        filter.doFilter(ctx, c -> {
            assertEquals("*", ctx.responseHeader("Access-Control-Allow-Origin"));
        });
    }

    @Test
    void toFilterShortCircuitsOnOptions() throws Exception {
        var filter = CorsFilter.builder().allowAllOrigins().build();
        var ctx = new StubHttpContext("OPTIONS", "/any");
        filter.doFilter(ctx, c -> fail("next.handle() should not be called for OPTIONS"));
        assertEquals(204, ctx.statusCode);
    }

    @Test
    void toFilterSetsPreflightHeadersOnOptions() throws Exception {
        var filter = CorsFilter.builder().allowAllOrigins().build();
        var ctx = new StubHttpContext("OPTIONS", "/any");
        filter.doFilter(ctx, c -> {});
        assertEquals("*", ctx.responseHeader("Access-Control-Allow-Origin"));
        assertEquals("GET, POST, PUT, DELETE, PATCH, OPTIONS", ctx.responseHeader("Access-Control-Allow-Methods"));
        assertEquals("Content-Type, Authorization", ctx.responseHeader("Access-Control-Allow-Headers"));
        assertEquals("3600", ctx.responseHeader("Access-Control-Max-Age"));
    }

    @Test
    void toFilterDoesNotSetOriginIfNull() throws Exception {
        var filter = CorsFilter.builder()
            .allowedOrigins(null)
            .build();
        var ctx = new StubHttpContext("GET", "/any");
        filter.doFilter(ctx, c -> assertNull(ctx.responseHeader("Access-Control-Allow-Origin")));
    }

    @Test
    void toFilterSetsCredentialsHeader() throws Exception {
        var filter = CorsFilter.builder()
            .allowedOrigins("https://app.example")
            .allowCredentials(true)
            .build();
        var ctx = new StubHttpContext("GET", "/any");
        filter.doFilter(ctx, c -> assertEquals("true", ctx.responseHeader("Access-Control-Allow-Credentials")));
    }
}
