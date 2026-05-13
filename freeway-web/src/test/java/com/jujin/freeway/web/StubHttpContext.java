package com.jujin.freeway.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal {@link HttpContext} for testing filters and handlers.
 * Keeps request and response headers in separate maps so test
 * assertions use the correct API ({@link #responseHeader} for
 * verifying what a filter wrote).
 */
public class StubHttpContext extends HttpContext {
    private final String method;
    private final String path;
    private final Map<String, String> requestHeaders = new LinkedHashMap<>();
    private final Map<String, String> responseHeaders = new LinkedHashMap<>();
    int statusCode = 200;
    boolean outputCalled;

    public StubHttpContext() { this("GET", "/"); }

    public StubHttpContext(String method, String path) {
        super(new DefaultJsonCodec());
        this.method = method;
        this.path = path;
    }

    public StubHttpContext requestHeader(String name, String value) {
        requestHeaders.put(name, value);
        return this;
    }

    // ── Request API ───────────────────────────────────────

    @Override public String method() { return method; }
    @Override public String path() { return path; }
    @Override public String queryParam(String name) { return null; }
    @Override public List<String> queryParams(String name) { return List.of(); }
    @Override public Map<String, List<String>> queryParams() { return Map.of(); }
    @Override public String header(String name) { return requestHeaders.get(name); }
    @Override public List<String> headers(String name) { return List.of(); }
    @Override public byte[] body() { return new byte[0]; }

    // ── Response API ──────────────────────────────────────

    @Override public HttpContext status(int status) { this.statusCode = status; return this; }
    @Override public HttpContext headerSet(String name, String value) {
        responseHeaders.put(name, value);
        return this;
    }
    @Override public String responseHeader(String name) { return responseHeaders.get(name); }
    @Override public HttpContext output(byte[] data) { outputCalled = true; return this; }

    @Override @SuppressWarnings("unchecked")
    public <T> T nativeRequest() { return null; }
    @Override @SuppressWarnings("unchecked")
    public <T> T nativeResponse() { return null; }
}
