package com.jujin.freeway.web;

import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * {@link HttpContext} implementation wrapping
 * {@link com.sun.net.httpserver.HttpExchange}.
 * <p>
 * Created by {@link WebModule} for each incoming request. The path
 * variables are set by the router after URI matching.
 * </p>
 */
public class JdkHttpContext extends HttpContext {

    private final HttpExchange exchange;
    private final Logger logger;
    private final Map<String, List<String>> queryParams;
    private volatile byte[] cachedBody;
    private int responseStatus = 200;
    private volatile boolean responded;

    public JdkHttpContext(HttpExchange exchange, JsonCodec jsonCodec, Logger logger) {
        super(jsonCodec);
        this.exchange = exchange;
        this.logger = logger;
        this.queryParams = parseQueryParams(exchange.getRequestURI().getRawQuery());
    }

    // ---- Request ----

    @Override
    public String method() {
        return exchange.getRequestMethod();
    }

    @Override
    public String path() {
        return exchange.getRequestURI().getPath();
    }

    @Override
    public String queryParam(String name) {
        var values = queryParams.get(name);
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }

    @Override
    public List<String> queryParams(String name) {
        return queryParams.getOrDefault(name, List.of());
    }

    @Override
    public Map<String, List<String>> queryParams() {
        return queryParams;
    }

    @Override
    public String header(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    @Override
    public List<String> headers(String name) {
        var vals = exchange.getRequestHeaders().get(name);
        return vals != null ? vals : List.of();
    }

    @Override
    public byte[] body() throws IOException {
        if (cachedBody == null) {
            try (var is = exchange.getRequestBody()) {
                if (maxBodySize > 0) {
                    cachedBody = is.readNBytes((int) maxBodySize);
                    if (is.read() != -1) {
                        throw new IOException("Request body exceeds maximum size of " + maxBodySize + " bytes");
                    }
                } else {
                    cachedBody = is.readAllBytes();
                }
            }
        }
        return cachedBody;
    }

    // ---- Response ----

    @Override
    public HttpContext status(int status) {
        this.responseStatus = status;
        return this;
    }

    @Override
    public HttpContext headerSet(String name, String value) {
        exchange.getResponseHeaders().set(name, value);
        return this;
    }

    @Override
    public String responseHeader(String name) {
        return exchange.getResponseHeaders().getFirst(name);
    }

    @Override
    public HttpContext output(byte[] data) throws IOException {
        if (responded) {
            logger.warn("Response already written — ignoring duplicate output() call");
            return this;
        }
        exchange.sendResponseHeaders(responseStatus, data.length);
        responded = true;
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
        return this;
    }

    // ---- Native access ----

    @Override
    @SuppressWarnings("unchecked")
    public <T> T nativeRequest() {
        return (T) exchange;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T nativeResponse() {
        return (T) exchange;
    }

    // ---- Internal ----

    private static Map<String, List<String>> parseQueryParams(String rawQuery) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank())
            return params;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String name = eq >= 0 ? decode(pair.substring(0, eq)) : decode(pair);
            String value = eq >= 0 ? decode(pair.substring(eq + 1)) : "";
            params.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }
        return params;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
