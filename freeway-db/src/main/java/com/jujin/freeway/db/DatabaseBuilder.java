package com.jujin.freeway.db;

import com.jujin.freeway.db.internal.ConnectionPool;
import com.jujin.freeway.db.internal.DatabaseImpl;
import com.jujin.freeway.db.internal.PoolConfig;

import java.sql.DriverManager;
import java.time.Duration;
import java.util.Properties;

/**
 * Builder for {@link Database}. Every setting has a sensible default —
 * typically only {@code url}, {@code username}, and {@code password} need to
 * be set.
 *
 * <pre>{@code
 * Database db = new DatabaseBuilder()
 *     .url("jdbc:postgresql://localhost:5432/mydb")
 *     .username("app")
 *     .password("secret")
 *     .maxSize(20)
 *     .build();
 * }</pre>
 */
public class DatabaseBuilder {

    String url;
    String username;
    String password;
    Properties extraProperties = new Properties();
    int maxSize = 10;
    int minIdle = 2;
    Duration connectionTimeout = Duration.ofSeconds(30);
    Duration maxLifetime = Duration.ofMinutes(30);
    Duration maxIdleTime = Duration.ofMinutes(10);
    Duration cleanInterval = Duration.ofMinutes(1);
    String healthCheckQuery = "SELECT 1";
    Duration healthCheckTimeout = Duration.ofSeconds(5);
    com.jujin.freeway.db.internal.DefaultRowMapper rowMapper;

    public DatabaseBuilder url(String url) {
        this.url = url;
        return this;
    }

    public DatabaseBuilder username(String username) {
        this.username = username;
        return this;
    }

    public DatabaseBuilder password(String password) {
        this.password = password;
        return this;
    }

    /** JDBC driver properties passed to {@link DriverManager#getConnection}. */
    public DatabaseBuilder property(String key, String value) {
        this.extraProperties.setProperty(key, value);
        return this;
    }

    /** Maximum number of connections (active + idle). Default 10. */
    public DatabaseBuilder maxSize(int maxSize) {
        this.maxSize = maxSize;
        return this;
    }

    /** Minimum idle connections kept warm. Default 2. */
    public DatabaseBuilder minIdle(int minIdle) {
        this.minIdle = minIdle;
        return this;
    }

    /** How long to wait for a connection before throwing. Default 30s. */
    public DatabaseBuilder connectionTimeout(Duration timeout) {
        this.connectionTimeout = timeout;
        return this;
    }

    /** Maximum age of a connection before it is evicted. Default 30m. */
    public DatabaseBuilder maxLifetime(Duration lifetime) {
        this.maxLifetime = lifetime;
        return this;
    }

    /** How long an idle connection may sit in the pool before eviction. Default 10m. */
    public DatabaseBuilder maxIdleTime(Duration idleTime) {
        this.maxIdleTime = idleTime;
        return this;
    }

    /** Interval between background pool maintenance runs. Default 1m. */
    public DatabaseBuilder cleanInterval(Duration interval) {
        this.cleanInterval = interval;
        return this;
    }

    /** Query used to validate a connection before handing it out. Default {@code SELECT 1}. */
    public DatabaseBuilder healthCheckQuery(String query) {
        this.healthCheckQuery = query;
        return this;
    }

    /** Timeout for the health-check query. Default 5s. */
    public DatabaseBuilder healthCheckTimeout(Duration timeout) {
        this.healthCheckTimeout = timeout;
        return this;
    }

    /**
     * Custom row mapper. When not set, a standalone mapper with basic type
     * coercion is used. Set this to a mapper backed by IoC
     * {@link com.jujin.freeway.ioc.coercion.TypeCoercer} and
     * {@link com.jujin.freeway.ioc.PropertyAccess} for richer conversions.
     */
    public DatabaseBuilder rowMapper(
        com.jujin.freeway.db.internal.DefaultRowMapper mapper) {
        this.rowMapper = mapper;
        return this;
    }

    /**
     * Builds the {@link Database}, immediately opening {@link #minIdle}
     * connections.
     */
    public Database build() {
        if (url == null || url.isBlank())
            throw new IllegalStateException("url is required");
        if (username == null)
            throw new IllegalStateException("username is required");
        if (password == null)
            throw new IllegalStateException("password is required");
        if (maxSize < 1)
            throw new IllegalStateException("maxSize must be >= 1");
        if (minIdle < 0 || minIdle > maxSize)
            throw new IllegalStateException("minIdle must be between 0 and maxSize");

        // Build JDBC connection properties
        var props = new Properties(extraProperties);
        props.setProperty("user", username);
        props.setProperty("password", password);

        var config = new PoolConfig(
            url, props,
            maxSize, minIdle,
            connectionTimeout, maxLifetime, maxIdleTime, cleanInterval,
            healthCheckQuery, healthCheckTimeout);

        var pool = new ConnectionPool(config);
        if (rowMapper != null) {
            return new DatabaseImpl(pool, rowMapper);
        }
        return new DatabaseImpl(pool);
    }
}
