package com.jujin.freeway.web;

import com.jujin.freeway.ioc.annotations.Symbol;

/**
 * An {@link HttpFilter} that adds CORS headers and handles OPTIONS preflight.
 * Configured via {@code cors.*} symbols; defaults allow all origins.
 *
 * <p>Contributed to {@link HttpFilterChain} automatically by {@link WebModule}
 * with id {@code "cors"} and constraint {@code "before:*"} (outermost).
 * Override via:</p>
 *
 * <pre>{@code
 * @Contribute(HttpFilterChain.class)
 * public static void filters(OrderedConfiguration<HttpFilter> config) {
 *     config.override("cors", myCorsFilter);   // custom filter
 *     // config.override("cors", null);        // disable CORS
 * }
 * }</pre>
 *
 * <h3>Configuration symbols</h3>
 * <table>
 *   <tr><td>cors.enabled</td><td>boolean, default true</td></tr>
 *   <tr><td>cors.allowed-origins</td><td>String: "*" or comma-separated origins</td></tr>
 *   <tr><td>cors.allowed-methods</td><td>String, default "GET, POST, PUT, DELETE, PATCH, OPTIONS"</td></tr>
 *   <tr><td>cors.allowed-headers</td><td>String, default "Content-Type, Authorization"</td></tr>
 *   <tr><td>cors.exposed-headers</td><td>String, default none</td></tr>
 *   <tr><td>cors.max-age</td><td>String, default "3600"</td></tr>
 *   <tr><td>cors.allow-credentials</td><td>boolean, default false</td></tr>
 * </table>
 *
 * <h3>application.json</h3>
 * <pre>{
 *   "cors": {
 *   allowed-origins: "https://app.example.com,https://admin.example.com"
 *   allow-credentials: true
 * </pre>
 */
public class CorsFilter implements HttpFilter {

    // ── Programmatic API (testing, non-IoC use) ──────────────────

    /**
     * Create a programmatically configured CORS filter via Builder.
     * For IoC-managed applications, prefer {@code cors.*} symbols in yml.
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for programmatic CORS configuration. */
    public static class Builder {

        private String allowedOrigins = "*";  // Default: allow all origins (consistent with IoC defaults)
        private String allowedMethods =
            "GET, POST, PUT, DELETE, PATCH, OPTIONS";
        private String allowedHeaders = "Content-Type, Authorization";
        private String exposedHeaders;
        private String maxAge = "3600";
        private boolean allowCredentials;

        public Builder allowAllOrigins() {
            this.allowedOrigins = "*";
            return this;
        }

        public Builder allowedOrigins(String origins) {
            this.allowedOrigins = origins;
            return this;
        }

        public Builder allowedMethods(String methods) {
            this.allowedMethods = methods;
            return this;
        }

        public Builder allowedHeaders(String headers) {
            this.allowedHeaders = headers;
            return this;
        }

        public Builder exposedHeaders(String headers) {
            this.exposedHeaders = headers;
            return this;
        }

        public Builder maxAge(String maxAge) {
            this.maxAge = maxAge;
            return this;
        }

        public Builder allowCredentials(boolean allow) {
            this.allowCredentials = allow;
            return this;
        }

        public CorsFilter build() {
            if (
                "*".equals(allowedOrigins) && allowCredentials
            ) throw new IllegalStateException(
                "Access-Control-Allow-Origin '*' cannot be used with Access-Control-Allow-Credentials: true"
            );
            return new CorsFilter(
                true,
                allowedOrigins,
                allowedMethods,
                allowedHeaders,
                exposedHeaders,
                maxAge,
                allowCredentials
            );
        }
    }

    // ── IoC-managed instance state ─────────────────────────────────

    private final boolean enabled;
    private final boolean allowAll;
    private final String[] allowedOriginList;
    private final String allowedMethods;
    private final String allowedHeaders;
    private final String exposedHeaders;
    private final String maxAge;
    private final boolean allowCredentials;

    public CorsFilter(
        @Symbol("cors.enabled") boolean enabled,
        @Symbol("cors.allowed-origins") String allowedOrigins,
        @Symbol("cors.allowed-methods") String allowedMethods,
        @Symbol("cors.allowed-headers") String allowedHeaders,
        @Symbol("cors.exposed-headers") String exposedHeaders,
        @Symbol("cors.max-age") String maxAge,
        @Symbol("cors.allow-credentials") boolean allowCredentials
    ) {
        this.enabled = enabled;
        boolean all = "*".equals(allowedOrigins);
        this.allowAll = all;
        this.allowedOriginList =
            all || allowedOrigins == null || allowedOrigins.isBlank()
                ? new String[0]
                : allowedOrigins.split("\\s*,\\s*");
        this.allowedMethods = allowedMethods;
        this.allowedHeaders = allowedHeaders;
        this.exposedHeaders = blankToNull(exposedHeaders);
        this.maxAge = maxAge;
        this.allowCredentials = allowCredentials && !all;
    }

    // ── HttpFilter ─────────────────────────────────────────────────

    @Override
    public void doFilter(HttpContext ctx, RouteHandler next) throws Exception {
        if (!enabled) {
            next.handle(ctx);
            return;
        }

        String requestOrigin = ctx.header("Origin");
        String acao = resolveAllowedOrigin(requestOrigin);

        if (acao != null) {
            ctx.headerSet("Access-Control-Allow-Origin", acao);
            if (!"*".equals(acao)) {
                ctx.headerSet("Vary", "Origin");
            }
        }
        if (allowCredentials) {
            ctx.headerSet("Access-Control-Allow-Credentials", "true");
        }
        if (exposedHeaders != null) {
            ctx.headerSet("Access-Control-Expose-Headers", exposedHeaders);
        }

        if ("OPTIONS".equalsIgnoreCase(ctx.method())) {
            if (allowedMethods != null) {
                ctx.headerSet("Access-Control-Allow-Methods", allowedMethods);
            }
            if (allowedHeaders != null) {
                ctx.headerSet("Access-Control-Allow-Headers", allowedHeaders);
            }
            if (maxAge != null) {
                ctx.headerSet("Access-Control-Max-Age", maxAge);
            }
            ctx.send(204, "");
            return;
        }

        next.handle(ctx);
    }

    private static String blankToNull(String s) {
        return s != null && !s.isBlank() ? s : null;
    }

    private String resolveAllowedOrigin(String requestOrigin) {
        if (allowAll) return "*";
        if (requestOrigin == null) return null;
        for (String o : allowedOriginList) {
            if (o.equals(requestOrigin)) return requestOrigin;
        }
        return null;
    }
}
