package com.jujin.freeway.db;

import com.jujin.freeway.db.annotations.Primary;
import com.jujin.freeway.db.internal.DbHubImpl;
import com.jujin.freeway.db.internal.DefaultRowMapper;
import com.jujin.freeway.db.internal.MigrationRunner;
import com.jujin.freeway.db.internal.RowMapperOverridesImpl;
import com.jujin.freeway.ioc.RegistryShutdownHub;
import com.jujin.freeway.ioc.ServiceBinder;
import com.jujin.freeway.ioc.annotations.*;
import com.jujin.freeway.ioc.classpath.ClassPathScanner;
import com.jujin.freeway.ioc.coercion.TypeCoercer;
import com.jujin.freeway.ioc.config.MappedConfiguration;
import com.jujin.freeway.ioc.property.PropertyAccess;
import com.jujin.freeway.ioc.symbol.SymbolProvider;
import com.jujin.freeway.ioc.symbol.SymbolSource;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Freeway DB IoC module — provides a singleton {@link Database} service backed
 * by the built-in connection pool, plus optional named datasources via
 * {@code freeway.db.datasources.<name>.*} config.
 *
 * <h3>Single database (default)</h3>
 * <pre>
 * freeway.db.url: jdbc:postgresql://localhost:5432/mydb
 * freeway.db.username: app
 * freeway.db.password: secret
 * </pre>
 *
 * {@code @Primary Database} or plain {@code Database} injects this default.
 *
 * <h3>Multiple named datasources</h3>
 * <pre>
 * freeway.db.datasources: replica, analytics
 *
 * freeway.db.datasources.replica.url: jdbc:postgresql://replica/mydb
 * freeway.db.datasources.replica.username: ro
 * freeway.db.datasources.replica.password: secret
 *
 * freeway.db.datasources.analytics.url: jdbc:clickhouse://...
 * freeway.db.datasources.analytics.username: an
 * freeway.db.datasources.analytics.password: secret
 * </pre>
 *
 * Named datasources are accessible via {@link DbHub#get(String)}.
 *
 * <h3>Read/write split with markers</h3>
 * Bind additional {@link Database} services with marker annotations in your
 * own module:
 *
 * <pre>{@code
 * binder.bind(Database.class, ReplicaDbBuilder.class)
 *     .withMarker(ReadOnly.class);
 * }</pre>
 */
@Marker(Builtin.class)
public class DbModule {

    private static final String PREFIX_DB = "freeway.db";
    private static final String PREFIX_DS = PREFIX_DB + ".datasources";

    // ── Bind services ────────────────────────────────────────────────

    public static void bind(ServiceBinder binder) {
        // Services are defined by build*() methods below, not via ServiceBinder.
        // ServiceBinder is used only for services without builder methods.
    }

    // ── Default configuration ────────────────────────────────────────

    @Contribute(SymbolProvider.class)
    @FactoryDefaults
    public static void contributeDefaults(MappedConfiguration<String, Object> config) {
        config.add(PREFIX_DB + ".pool.max-size", 10);
        config.add(PREFIX_DB + ".pool.min-idle", 2);
        config.add(PREFIX_DB + ".pool.connection-timeout", "30 s");
        config.add(PREFIX_DB + ".pool.max-lifetime", "30 m");
        config.add(PREFIX_DB + ".pool.max-idle-time", "10 m");
        config.add(PREFIX_DB + ".pool.clean-interval", "1 m");
        config.add(PREFIX_DB + ".pool.health-check-query", "SELECT 1");
        config.add(PREFIX_DB + ".pool.health-check-timeout", "5 s");
        // Migration
        config.add(PREFIX_DB + ".migration.enabled", true);
        config.add(PREFIX_DB + ".migration.path", "db/migration/");
    }

    // ── Default (primary) database ───────────────────────────────────

    @Marker(Primary.class)
    public static Database buildDatabase(
        @Symbol(PREFIX_DB + ".url") String url,
        @Symbol(PREFIX_DB + ".username") String username,
        @Symbol(PREFIX_DB + ".password") String password,
        @Symbol(PREFIX_DB + ".pool.max-size") int maxSize,
        @Symbol(PREFIX_DB + ".pool.min-idle") int minIdle,
        @Symbol(PREFIX_DB + ".pool.connection-timeout")
        @com.jujin.freeway.ioc.annotations.IntermediateType(
            com.jujin.freeway.ioc.schedule.TimeInterval.class) int connTimeoutMs,
        @Symbol(PREFIX_DB + ".pool.max-lifetime")
        @com.jujin.freeway.ioc.annotations.IntermediateType(
            com.jujin.freeway.ioc.schedule.TimeInterval.class) int maxLifetimeMs,
        @Symbol(PREFIX_DB + ".pool.max-idle-time")
        @com.jujin.freeway.ioc.annotations.IntermediateType(
            com.jujin.freeway.ioc.schedule.TimeInterval.class) int maxIdleTimeMs,
        @Symbol(PREFIX_DB + ".pool.clean-interval")
        @com.jujin.freeway.ioc.annotations.IntermediateType(
            com.jujin.freeway.ioc.schedule.TimeInterval.class) int cleanIntervalMs,
        @Symbol(PREFIX_DB + ".pool.health-check-query") String healthQuery,
        @Symbol(PREFIX_DB + ".pool.health-check-timeout")
        @com.jujin.freeway.ioc.annotations.IntermediateType(
            com.jujin.freeway.ioc.schedule.TimeInterval.class) int healthTimeoutMs,
        RegistryShutdownHub shutdownHub,
        TypeCoercer typeCoercer,
        PropertyAccess propertyAccess,
        RowMapperOverrides rowMapperOverrides) {

        Database db = new DatabaseBuilder()
            .url(url)
            .username(username)
            .password(password)
            .maxSize(maxSize)
            .minIdle(minIdle)
            .connectionTimeout(Duration.ofMillis(connTimeoutMs))
            .maxLifetime(Duration.ofMillis(maxLifetimeMs))
            .maxIdleTime(Duration.ofMillis(maxIdleTimeMs))
            .cleanInterval(Duration.ofMillis(cleanIntervalMs))
            .healthCheckQuery(healthQuery)
            .healthCheckTimeout(Duration.ofMillis(healthTimeoutMs))
            .rowMapper(new DefaultRowMapper(typeCoercer, propertyAccess, rowMapperOverrides))
            .build();

        shutdownHub.addRegistryShutdownListener(db::close);
        return db;
    }

    // ── RowMapper overrides ──────────────────────────────────────────

    /** Builds the contribution-based RowMapper overrides from user modules. */
    public static RowMapperOverrides buildRowMapperOverrides(
        Map<Class<?>, RowMapper<?>> configuration) {
        return new RowMapperOverridesImpl(configuration);
    }

    // ── Named datasources ────────────────────────────────────────────

    /** Builds named databases from {@code freeway.db.datasources.<name>.*} config. */
    public static DbHub buildDatabases(
        @Primary Database primary,
        SymbolSource symbols,
        RegistryShutdownHub shutdownHub,
        TypeCoercer typeCoercer,
        PropertyAccess propertyAccess,
        RowMapperOverrides rowMapperOverrides,
        Logger logger) {

        String names = symbols.valueForSymbol(PREFIX_DS);
        if (names == null || names.isBlank()) {
            return new DbHubImpl(Map.of("primary", primary));
        }

        Map<String, Database> map = new HashMap<>();
        map.put("primary", primary);

        for (String name : names.split(",")) {
            name = name.trim();
            if (name.isEmpty() || map.containsKey(name)) continue;

            String dsPrefix = PREFIX_DS + "." + name;
            String url = symbols.valueForSymbol(dsPrefix + ".url");
            String username = symbols.valueForSymbol(dsPrefix + ".username");
            String password = symbols.valueForSymbol(dsPrefix + ".password");

            if (url == null || username == null || password == null) {
                logger.warn("Freeway DB: skipping datasource '{}' — url/username/password not set", name);
                continue;
            }

            Database db = new DatabaseBuilder()
                .url(url)
                .username(username)
                .password(password)
                .maxSize(intSymbol(symbols, dsPrefix + ".pool.max-size", PREFIX_DB + ".pool.max-size", 10))
                .minIdle(intSymbol(symbols, dsPrefix + ".pool.min-idle", PREFIX_DB + ".pool.min-idle", 2))
                .connectionTimeout(durationSymbol(symbols, dsPrefix + ".pool.connection-timeout",
                    PREFIX_DB + ".pool.connection-timeout", Duration.ofSeconds(30)))
                .maxLifetime(durationSymbol(symbols, dsPrefix + ".pool.max-lifetime",
                    PREFIX_DB + ".pool.max-lifetime", Duration.ofMinutes(30)))
                .maxIdleTime(durationSymbol(symbols, dsPrefix + ".pool.max-idle-time",
                    PREFIX_DB + ".pool.max-idle-time", Duration.ofMinutes(10)))
                .rowMapper(new DefaultRowMapper(typeCoercer, propertyAccess, rowMapperOverrides))
                .build();

            shutdownHub.addRegistryShutdownListener(db::close);
            map.put(name, db);
            logger.info("Freeway DB: datasource '{}' ready", name);
        }

        return new DbHubImpl(map);
    }

    // ── Health check ─────────────────────────────────────────────────

    @Startup
    public static void ping(@Primary Database database, Logger logger) {
        if (database.ping()) {
            var stats = database.stats();
            logger.info("Freeway DB: connected (pool: {} total, {} idle, {} active, max {})",
                stats.totalConnections(), stats.idleConnections(),
                stats.activeConnections(), stats.maxConnections());
        } else {
            logger.warn("Freeway DB: ping failed — check connection settings");
        }
    }

    // ── Migrations ───────────────────────────────────────────────────

    @Startup
    public static void runMigrations(
        @Primary Database database,
        ClassPathScanner scanner,
        Logger logger,
        @Symbol(PREFIX_DB + ".migration.enabled") boolean enabled,
        @Symbol(PREFIX_DB + ".migration.path") String path) {

        if (!enabled) {
            logger.debug("Freeway DB: migrations disabled");
            return;
        }

        new MigrationRunner(database, scanner, path, logger).run();
    }

    // ── Config helpers ───────────────────────────────────────────────

    private static int intSymbol(SymbolSource symbols, String key, String fallbackKey, int defaultValue) {
        String val = symbols.valueForSymbol(key);
        if (val != null) return Integer.parseInt(val);
        val = symbols.valueForSymbol(fallbackKey);
        return val != null ? Integer.parseInt(val) : defaultValue;
    }

    private static Duration durationSymbol(SymbolSource symbols, String key,
                                            String fallbackKey, Duration defaultValue) {
        String val = symbols.valueForSymbol(key);
        if (val == null) val = symbols.valueForSymbol(fallbackKey);
        if (val == null) return defaultValue;
        // Accept "30 s", "30s", "30" (seconds), or milliseconds
        String trimmed = val.trim();
        try {
            if (trimmed.endsWith("ms")) return Duration.ofMillis(Long.parseLong(
                trimmed.substring(0, trimmed.length() - 2).trim()));
            if (trimmed.endsWith("s")) return Duration.ofSeconds(Long.parseLong(
                trimmed.substring(0, trimmed.length() - 1).trim()));
            if (trimmed.endsWith("m")) return Duration.ofMinutes(Long.parseLong(
                trimmed.substring(0, trimmed.length() - 1).trim()));
            if (trimmed.endsWith("h")) return Duration.ofHours(Long.parseLong(
                trimmed.substring(0, trimmed.length() - 1).trim()));
            return Duration.ofMillis(Long.parseLong(trimmed));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
