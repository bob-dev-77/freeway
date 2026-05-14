package com.jujin.freeway.db;

import com.jujin.freeway.ioc.symbol.SymbolSource;
import java.time.Duration;
import java.util.Properties;

/**
 * Immutable snapshot of all configuration needed to build a
 * {@link Database}. Use for IoC wiring or caching; for standalone
 * creation prefer the fluent {@link DatabaseBuilder}.
 *
 * <h3>Quick-start (only these 3 are required)</h3>
 * <pre>{@code
 * var config = new DatabaseConfig(
 *     "jdbc:postgresql://localhost/mydb", "app", "secret");
 * }</pre>
 *
 * <h3>From symbol source (IoC path)</h3>
 * <pre>{@code
 * var config = DatabaseConfig.fromSymbols(symbols, "freeway.db");
 * }</pre>
 *
 * <h3>Parameter reference</h3>
 * <table>
 *   <caption>Parameter classification by usage frequency</caption>
 *   <tr><th>Frequency</th><th>Parameters</th></tr>
 *   <tr><td>🔴 <b>Required</b></td>
 *       <td>{@code url}, {@code username}, {@code password}</td></tr>
 *   <tr><td>🟠 <b>Common</b> (20-50%)</td>
 *       <td>{@code maxSize}, {@code queryTimeout}, {@code connectionTimeout}</td></tr>
 *   <tr><td>🟡 <b>Occasional</b> (10-20%)</td>
 *       <td>{@code minIdle}, {@code maxLifetime}, {@code healthCheckTimeout}</td></tr>
 *   <tr><td>🟢 <b>Rare</b> (&lt;10%)</td>
 *       <td>{@code maxIdleTime}, {@code healthCheckQuery}, {@code cleanInterval},
 *           {@code extraProperties}</td></tr>
 * </table>
 *
 * <h3>Design rationale</h3>
 * <p>
 * Defaults follow <a href="https://github.com/brettwooldridge/HikariCP">HikariCP</a>
 * best practices. Key principles:
 * <ul>
 *   <li><b>connectionTimeout</b> (10s) — aligned with typical HTTP timeout stacks.
 *       30s would leave no room for the actual query if the HTTP layer times out at 30s.</li>
 *   <li><b>healthCheckQuery</b> defaulting to {@code null} — modern JDBC4+ drivers
 *       (all drivers since Java 6, 2006) support {@code Connection.isValid()}, a
 *       wire-protocol ping with zero SQL overhead. Only set a query for ancient drivers.</li>
 *   <li><b>queryTimeout</b> (15s) — protects against runaway queries. HikariCP omits
 *       this at the pool level, but we default it because an unbound query is a
 *       production risk.</li>
 *   <li><b>maxLifetime</b> (30m) — safely below MySQL's default {@code wait_timeout}
 *       (8h) and most cloud proxy timeouts.</li>
 * </ul>
 */
public record DatabaseConfig(
    /** JDBC URL, e.g. {@code jdbc:postgresql://localhost:5432/mydb} — REQUIRED */
    String url,

    /** Database username — REQUIRED */
    String username,

    /** Database password — REQUIRED */
    String password,

    /**
     * Extra JDBC driver properties (SSL, timezone, application name, etc.).
     * Rarely needed — most drivers work with just url/username/password.
     */
    Properties extraProperties,

    /**
     * Maximum connections in the pool (active + idle).
     * <p><b>Default: 10.</b> For most apps 5-20 is sufficient.
     * Start low and increase only under measured pool exhaustion.
     * <p><b>Change when:</b> long-running queries or high concurrent load.
     */
    int maxSize,

    /**
     * Minimum idle connections to maintain.
     * <p><b>Default: 2.</b> Small buffer for traffic bursts.
     * <p><b>Change when:</b> cold-start latency matters under bursts,
     * or DB connection latency is high (&gt;50ms).
     */
    int minIdle,

    /**
     * Max time to wait for a connection from the pool.
     * <p><b>Default: 10 seconds.</b> Align with your HTTP timeout stack.
     * <p><b>Change when:</b> latency-sensitive APIs (1-3s) or batch jobs (15-30s).
     */
    Duration connectionTimeout,

    /**
     * Max lifetime of a connection. After this the connection is retired
     * on next checkout or evicted by the background cleaner.
     * <p><b>Default: 30 minutes.</b> Safely below common DB server timeouts.
     * Always keep shorter than the server's timeout by at least 5 min.
     * <p><b>Change when:</b> aggressive firewalls/NATs or cloud DB proxies.
     */
    Duration maxLifetime,

    /**
     * Max time an idle connection stays in the pool before eviction.
     * <p><b>Default: 10 minutes.</b>
     * <p><b>Change when:</b> behind a cloud DB proxy with aggressive idle
     * disconnect (e.g. AWS RDS Proxy: 3-5 min).
     */
    Duration maxIdleTime,

    /**
     * Interval between background pool maintenance runs.
     * <p><b>Default: 2 minutes.</b>
     * <p><b>Change when:</b> rarely needed.
     */
    Duration cleanInterval,

    /**
     * Optional SQL for health checks (e.g. {@code SELECT 1}).
     * <p><b>Default: null</b> — uses {@code Connection.isValid()} (JDBC4
     * wire-protocol ping, no SQL round-trip). Set only for pre-JDBC4 drivers.
     * <p><b>Change when:</b> using an ancient JDBC driver.
     */
    String healthCheckQuery,

    /**
     * Timeout for connection health check.
     * <p><b>Default: 5 seconds.</b> Healthy DB responds in &lt;100ms.
     * <p><b>Change when:</b> high-throughput systems (1-2s) or
     * far-away / overloaded databases (10s).
     */
    Duration healthCheckTimeout,

    /**
     * Timeout for individual SQL statements.
     * <p><b>Default: 15 seconds.</b> Protects against runaway queries.
     * OLTP: 5-15s; reporting: 30-120s; DDL: 0 (no timeout).
     * <p><b>Change when:</b> always align with your app's acceptable latency.
     */
    Duration queryTimeout
) {
    // ═══════════════════════════════════════════════════════════════════
    // Best-practice default values (industry consensus, HikariCP-aligned)
    // ═══════════════════════════════════════════════════════════════════

    /** Default pool size: sufficient for most apps with 2-4 CPU cores. */
    public static final int DEFAULT_MAX_SIZE = 10;

    /** Minimum idle connections to keep warm for traffic bursts. */
    public static final int DEFAULT_MIN_IDLE = 2;

    /**
     * Max time to wait for a connection (10s). Aligns with typical HTTP
     * timeout stacks so there's time left for the actual query.
     */
    public static final Duration DEFAULT_CONNECTION_TIMEOUT =
        Duration.ofSeconds(10);

    /**
     * Max connection lifetime (30m). Safely below MySQL's default
     * {@code wait_timeout} (8h) and most cloud proxy timeouts.
     */
    public static final Duration DEFAULT_MAX_LIFETIME = Duration.ofMinutes(30);

    /** Max idle time before a connection is eligible for eviction. */
    public static final Duration DEFAULT_MAX_IDLE_TIME = Duration.ofMinutes(10);

    /** Interval between background pool maintenance runs. */
    public static final Duration DEFAULT_CLEAN_INTERVAL = Duration.ofMinutes(2);

    /**
     * Health check query default: {@code null} means use
     * {@code Connection.isValid()} (JDBC4 wire-protocol ping,
     * no SQL round-trip). Set only for pre-JDBC4 drivers.
     */
    public static final String DEFAULT_HEALTH_CHECK_QUERY = null;

    /** Timeout for connection health checks. */
    public static final Duration DEFAULT_HEALTH_CHECK_TIMEOUT =
        Duration.ofSeconds(5);

    /**
     * SQL statement timeout (15s). Protects against runaway queries.
     * HikariCP omits this at the pool level, but we default it because
     * an unbound query is a production risk.
     */
    public static final Duration DEFAULT_QUERY_TIMEOUT = Duration.ofSeconds(15);

    /**
     * Compact constructor for the common case — only url/username/password
     * are required; everything else gets the defaults above.
     */
    public DatabaseConfig(String url, String username, String password) {
        this(
            url,
            username,
            password,
            new Properties(),
            DEFAULT_MAX_SIZE,
            DEFAULT_MIN_IDLE,
            DEFAULT_CONNECTION_TIMEOUT,
            DEFAULT_MAX_LIFETIME,
            DEFAULT_MAX_IDLE_TIME,
            DEFAULT_CLEAN_INTERVAL,
            DEFAULT_HEALTH_CHECK_QUERY,
            DEFAULT_HEALTH_CHECK_TIMEOUT,
            DEFAULT_QUERY_TIMEOUT
        );
    }

    /** Defaults used when {@link #fromSymbols} finds no override. */
    public static DatabaseConfig defaults() {
        return new DatabaseConfig(
            null,
            null,
            null,
            new Properties(),
            DEFAULT_MAX_SIZE,
            DEFAULT_MIN_IDLE,
            DEFAULT_CONNECTION_TIMEOUT,
            DEFAULT_MAX_LIFETIME,
            DEFAULT_MAX_IDLE_TIME,
            DEFAULT_CLEAN_INTERVAL,
            DEFAULT_HEALTH_CHECK_QUERY,
            DEFAULT_HEALTH_CHECK_TIMEOUT,
            DEFAULT_QUERY_TIMEOUT
        );
    }

    /**
     * Creates a {@link DatabaseConfig} by reading properties from a
     * {@link SymbolSource} under the given prefix. Missing keys fall back
     * to {@link #defaults()}.
     *
     * <p>Accepted suffixes (relative to {@code prefix}):
     * <table>
     *   <caption>Configuration keys</caption>
     *   <tr><th>Suffix</th><th>Type</th><th>Default</th></tr>
     *   <tr><td>{@code .url}</td><td>String</td><td><em>required</em></td></tr>
     *   <tr><td>{@code .username}</td><td>String</td><td><em>required</em></td></tr>
     *   <tr><td>{@code .password}</td><td>String</td><td><em>required</em></td></tr>
     *   <tr><td>{@code .pool.max-size}</td><td>int</td><td>10</td></tr>
     *   <tr><td>{@code .pool.min-idle}</td><td>int</td><td>2</td></tr>
     *   <tr><td>{@code .pool.connection-timeout}</td><td>Duration</td><td>10 s</td></tr>
     *   <tr><td>{@code .pool.max-lifetime}</td><td>Duration</td><td>30 m</td></tr>
     *   <tr><td>{@code .pool.max-idle-time}</td><td>Duration</td><td>10 m</td></tr>
     *   <tr><td>{@code .pool.clean-interval}</td><td>Duration</td><td>2 m</td></tr>
     *   <tr><td>{@code .pool.health-check-query}</td><td>String</td><td><em>none (uses isValid)</em></td></tr>
     *   <tr><td>{@code .pool.health-check-timeout}</td><td>Duration</td><td>5 s</td></tr>
     *   <tr><td>{@code .query-timeout}</td><td>Duration</td><td>15 s</td></tr>
     * </table>
     */
    public static DatabaseConfig fromSymbols(
        SymbolSource symbols,
        String prefix
    ) {
        var d = defaults();
        return new DatabaseConfig(
            symbols.resolve(prefix + ".url"),
            symbols.resolve(prefix + ".username"),
            symbols.resolve(prefix + ".password"),
            new Properties(),
            intSymbol(symbols, prefix + ".pool.max-size", d.maxSize()),
            intSymbol(symbols, prefix + ".pool.min-idle", d.minIdle()),
            durationSymbol(
                symbols,
                prefix + ".pool.connection-timeout",
                d.connectionTimeout()
            ),
            durationSymbol(
                symbols,
                prefix + ".pool.max-lifetime",
                d.maxLifetime()
            ),
            durationSymbol(
                symbols,
                prefix + ".pool.max-idle-time",
                d.maxIdleTime()
            ),
            durationSymbol(
                symbols,
                prefix + ".pool.clean-interval",
                d.cleanInterval()
            ),
            stringSymbol(
                symbols,
                prefix + ".pool.health-check-query",
                d.healthCheckQuery()
            ),
            durationSymbol(
                symbols,
                prefix + ".pool.health-check-timeout",
                d.healthCheckTimeout()
            ),
            durationSymbol(symbols, prefix + ".query-timeout", d.queryTimeout())
        );
    }

    // ── Symbol helpers ──────────────────────────────────────────────

    private static int intSymbol(
        SymbolSource symbols,
        String key,
        int defaultValue
    ) {
        if (!symbols.contains(key)) return defaultValue;
        String val = symbols.resolve(key);
        if (val != null) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private static Duration durationSymbol(
        SymbolSource symbols,
        String key,
        Duration defaultValue
    ) {
        if (!symbols.contains(key)) return defaultValue;
        String val = symbols.resolve(key);
        if (val == null) return defaultValue;
        String trimmed = val.trim();
        try {
            if (trimmed.endsWith("ms")) return Duration.ofMillis(
                Long.parseLong(
                    trimmed.substring(0, trimmed.length() - 2).trim()
                )
            );
            if (trimmed.endsWith("s")) return Duration.ofSeconds(
                Long.parseLong(
                    trimmed.substring(0, trimmed.length() - 1).trim()
                )
            );
            if (trimmed.endsWith("m")) return Duration.ofMinutes(
                Long.parseLong(
                    trimmed.substring(0, trimmed.length() - 1).trim()
                )
            );
            if (trimmed.endsWith("h")) return Duration.ofHours(
                Long.parseLong(
                    trimmed.substring(0, trimmed.length() - 1).trim()
                )
            );
            return Duration.ofMillis(Long.parseLong(trimmed));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String stringSymbol(
        SymbolSource symbols,
        String key,
        String defaultValue
    ) {
        if (!symbols.contains(key)) return defaultValue;
        String val = symbols.resolve(key);
        return val != null ? val : defaultValue;
    }
}
