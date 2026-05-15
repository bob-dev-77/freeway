package com.jujin.freeway.db;

import com.jujin.freeway.db.annotations.Primary;
import com.jujin.freeway.db.internal.DbHubImpl;
import com.jujin.freeway.db.internal.DefaultRowMapper;
import com.jujin.freeway.db.internal.MigrationRunner;
import com.jujin.freeway.db.internal.RowMapperOverridesImpl;
import com.jujin.freeway.ioc.RegistryShutdownHub;
import com.jujin.freeway.ioc.ServiceBinder;
import com.jujin.freeway.ioc.annotations.Builtin;
import com.jujin.freeway.ioc.annotations.Contribute;
import com.jujin.freeway.ioc.annotations.FactoryDefaults;
import com.jujin.freeway.ioc.annotations.Marker;
import com.jujin.freeway.ioc.annotations.Startup;
import com.jujin.freeway.ioc.annotations.Symbol;
import com.jujin.freeway.ioc.classpath.ClassPathScanner;
import com.jujin.freeway.ioc.coercion.TypeCoercer;
import com.jujin.freeway.ioc.config.MappedConfiguration;
import com.jujin.freeway.ioc.property.PropertyAccess;
import com.jujin.freeway.ioc.symbol.SymbolProvider;
import com.jujin.freeway.ioc.symbol.SymbolSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;

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
    public static void contributeDefaults(
        MappedConfiguration<String, Object> config
    ) {
        // Migration (pool defaults live in DatabaseConfig)
        config.add(PREFIX_DB + ".migration.enabled", true);
        config.add(PREFIX_DB + ".migration.path", "db/migration/");
    }

    // ── Configuration factory ───────────────────────────────────────

    public static DatabaseConfig buildDatabaseConfig(SymbolSource symbols) {
        return DatabaseConfig.fromSymbols(symbols, PREFIX_DB);
    }

    // ── Default (primary) database ───────────────────────────────────

    @Marker(Primary.class)
    public static Database buildDatabase(
        DatabaseConfig config,
        RegistryShutdownHub shutdownHub,
        TypeCoercer typeCoercer,
        PropertyAccess propertyAccess,
        RowMapperOverrides rowMapperOverrides
    ) {
        Database db = new DatabaseBuilder()
            .fromConfig(config)
            .rowMapper(
                new DefaultRowMapper(
                    typeCoercer,
                    propertyAccess,
                    rowMapperOverrides
                )
            )
            .build();

        shutdownHub.addRegistryShutdownListener(db::close);
        return db;
    }

    // ── RowMapper overrides ──────────────────────────────────────────

    /** Builds the contribution-based RowMapper overrides from user modules. */
    public static RowMapperOverrides buildRowMapperOverrides(
        Map<Class<?>, RowMapper<?>> configuration
    ) {
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
        Logger logger
    ) {
        String names = symbols.resolve(PREFIX_DS);
        if (names == null || names.isBlank()) {
            return new DbHubImpl(Map.of("primary", primary));
        }

        Map<String, Database> map = new HashMap<>();
        map.put("primary", primary);

        var errors = new ArrayList<String>();
        for (String name : names.split(",")) {
            name = name.trim();
            if (name.isEmpty() || map.containsKey(name)) continue;

            String dsPrefix = PREFIX_DS + "." + name;
            var config = DatabaseConfig.fromSymbols(symbols, dsPrefix);

            // Fail fast on incomplete datasource configuration
            if (config.url() == null || config.username() == null || config.password() == null) {
                errors.add(
                    "Datasource '" + name + "' is missing required config: " +
                        (config.url() == null ? "url " : "") +
                        (config.username() == null ? "username " : "") +
                        (config.password() == null ? "password" : "")
                );
                continue;
            }

            Database db = new DatabaseBuilder()
                .fromConfig(config)
                .rowMapper(
                    new DefaultRowMapper(
                        typeCoercer,
                        propertyAccess,
                        rowMapperOverrides
                    )
                )
                .build();

            shutdownHub.addRegistryShutdownListener(db::close);
            map.put(name, db);
            logger.info("Freeway DB: datasource '{}' ready", name);
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                "Freeway DB: Failed to initialize named datasources:\n" +
                    String.join("\n", errors)
            );
        }

        return new DbHubImpl(map);
    }

    // ── Health check ─────────────────────────────────────────────────

    @Startup
    public static void ping(@Primary Database database, Logger logger) {
        if (database.ping()) {
            var stats = database.stats();
            logger.info(
                "Freeway DB: connected (pool: {} total, {} idle, {} active, max {})",
                stats.totalConnections(),
                stats.idleConnections(),
                stats.activeConnections(),
                stats.maxConnections()
            );
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
        @Symbol(PREFIX_DB + ".migration.path") String path
    ) {
        if (!enabled) {
            logger.debug("Freeway DB: migrations disabled");
            return;
        }

        new MigrationRunner(database, scanner, path, logger).run();
    }
}
