package com.jujin.freeway.db;

import java.util.Map;

/**
 * A registry of named {@link Database} instances auto-created from
 * {@code freeway.db.datasources.<name>.*} configuration.
 *
 * <pre>{@code
 * // YAML:
 * freeway.db.datasources.analytics.url: jdbc:postgresql://...
 * freeway.db.datasources.analytics.username: app
 * freeway.db.datasources.analytics.password: secret
 *
 * // Java:
 * @Inject DbHub dbHub;
 * Database analytics = dbHub.get("analytics");
 * }</pre>
 *
 * <p>
 * For compile-time type safety with fewer data sources, use marker
 * annotations ({@code @Primary}, {@code @ReadOnly}) directly:
 *
 * <pre>{@code
 * @Primary Database primary;
 * @ReadOnly Database replica;
 * }</pre>
 */
public interface DbHub {

    /** Returns the database registered under {@code name}, or {@code null}. */
    Database get(String name);

    /** All registered databases, keyed by name. */
    Map<String, Database> all();
}
