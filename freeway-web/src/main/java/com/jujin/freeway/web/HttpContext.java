package com.jujin.freeway.web;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unified request/response context, server-agnostic.
 * <p>
 * Concrete subclasses adapt specific HTTP server implementations (e.g. JDK
 * HttpServer, Undertow, etc.). Route handlers receive a {@code FreewayContext}
 * and never depend on the underlying server.
 * </p>
 */
public abstract class HttpContext {

    private Map<String, String> pathVariables = Map.of();
    protected final JsonCodec jsonCodec;
    private static final Pattern CHARSET_PATTERN = Pattern.compile("(?i)\\bcharset=([^\\s;]+)");
    protected long maxBodySize = 10_485_760; // 10 MB default

    protected HttpContext(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    // ---- Path variables (set by router after matching) ----

    /** Set path variables extracted from route matching. */
    public void pathVariables(Map<String, String> vars) {
        this.pathVariables = vars != null ? Map.copyOf(vars) : Map.of();
    }

    /** Get a path variable by name. */
    public String pathVar(String name) {
        return pathVariables.get(name);
    }

    /** Get all path variables as an immutable map. */
    public Map<String, String> pathVars() {
        return pathVariables;
    }

    // ---- Request API (subclasses must implement) ----

    /** HTTP method (GET, POST, ...). */
    public abstract String method();

    /** Request URI path. */
    public abstract String path();

    /** Get a single query parameter value (first if multiple). */
    public abstract String queryParam(String name);

    /** Get all query parameter values for a given name. */
    public abstract java.util.List<String> queryParams(String name);

    /** Get all query parameters. */
    public abstract Map<String, java.util.List<String>> queryParams();

    /** Get a request header (first value). */
    public abstract String header(String name);

    /** Get all values for a request header. */
    public abstract java.util.List<String> headers(String name);

    /** Read request body as byte array. */
    public abstract byte[] body() throws IOException;

    /** Set max body size in bytes (0 = unlimited). Default is 10 MB. */
    public void maxBodySize(long maxBodySize) {
        this.maxBodySize = maxBodySize;
    }

    /** Read request body as a string, decoding according to the request charset (default UTF-8). */
    public String bodyText() throws IOException {
        return new String(body(), charsetFromContentType());
    }

    private Charset charsetFromContentType() {
        String ct = header("Content-Type");
        if (ct != null) {
            Matcher m = CHARSET_PATTERN.matcher(ct);
            if (m.find()) {
                try {
                    return Charset.forName(m.group(1));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return StandardCharsets.UTF_8;
    }

    /** Parse request body as JSON and convert to the given type. */
    public <T> T bodyAsJson(Class<T> type) throws IOException {
        checkJsonContentType();
        return jsonCodec.fromJson(bodyText(), type);
    }

    /** Parse request body as JSON and convert to the given generic type. */
    public <T> T bodyAsJson(java.lang.reflect.Type type) throws IOException {
        checkJsonContentType();
        return jsonCodec.fromJson(bodyText(), type);
    }

    private void checkJsonContentType() throws IOException {
        String ct = header("Content-Type");
        if (ct != null && !ct.isBlank() && !ct.toLowerCase().contains("json")) {
            throw new IOException("Expected application/json but got " + ct);
        }
    }

    // ---- Response API (subclasses must implement) ----

    /** Set the response status code. Returns this for chaining. */
    public abstract HttpContext status(int status);

    /** Set a response header. Returns this for chaining. */
    public abstract HttpContext headerSet(String name, String value);

    /**
     * Read back a response header previously set via {@link #headerSet}.
     * Default returns null; subclasses with access to the underlying
     * response object should override.
     */
    public String responseHeader(String name) {
        return null;
    }

    /** Write bytes to the response body (terminal). Returns this for chaining. */
    public abstract HttpContext output(byte[] data) throws IOException;

    /**
     * Write a UTF-8 string to the response body (terminal). Returns this for
     * chaining.
     */
    public HttpContext output(String text) throws IOException {
        output(text.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    /**
     * Serialize object as JSON and write to response body (terminal). Sets
     * {@code Content-Type: application/json; charset=utf-8} automatically.
     * Returns this for chaining.
     */
    public HttpContext outputJson(Object value) throws IOException {
        headerSet("Content-Type", "application/json; charset=utf-8");
        output(jsonCodec.toJson(value).getBytes(StandardCharsets.UTF_8));
        return this;
    }

    // ---- Convenience methods (built on the above) ----

    /**
     * Send a plain text response with the given status code. Returns this for
     * chaining. Skips Content-Type for status codes that must not have a body
     * (204, 304).
     */
    public HttpContext send(int status, String text) throws IOException {
        status(status);
        if (status != 204 && status != 304) {
            headerSet("Content-Type", "text/plain; charset=utf-8");
        }
        return output(text);
    }

    /**
     * Send a JSON response with the given status code. Returns this for chaining.
     */
    public HttpContext sendJson(int status, Object value) throws IOException {
        status(status);
        headerSet("Content-Type", "application/json; charset=utf-8");
        return outputJson(value);
    }

    // ---- Utility ----

    /** Returns null if the string is null or blank, otherwise the string itself. */
    protected static String blankToNull(String s) {
        return s != null && !s.isBlank() ? s : null;
    }

    // ---- Native access (escape hatch for server-specific features) ----

    /**
     * Return the underlying native request object. Cast to the server-specific
     * type.
     */
    public abstract <T> T nativeRequest();

    /**
     * Return the underlying native response object. Cast to the server-specific
     * type.
     */
    public abstract <T> T nativeResponse();
}
