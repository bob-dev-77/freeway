package com.jujin.freeway.db;

import com.jujin.freeway.db.internal.ConnectionPool;
import com.jujin.freeway.db.internal.DatabaseImpl;
import com.jujin.freeway.db.internal.PoolConfig;
import com.jujin.freeway.db.internal.RowMapperFactory;
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
    int maxSize = DatabaseConfig.DEFAULT_MAX_SIZE;
    int minIdle = DatabaseConfig.DEFAULT_MIN_IDLE;
    Duration connectionTimeout = DatabaseConfig.DEFAULT_CONNECTION_TIMEOUT;
    Duration maxLifetime = DatabaseConfig.DEFAULT_MAX_LIFETIME;
    Duration maxIdleTime = DatabaseConfig.DEFAULT_MAX_IDLE_TIME;
    Duration cleanInterval = DatabaseConfig.DEFAULT_CLEAN_INTERVAL;
    String healthCheckQuery = DatabaseConfig.DEFAULT_HEALTH_CHECK_QUERY;
    Duration healthCheckTimeout = DatabaseConfig.DEFAULT_HEALTH_CHECK_TIMEOUT;
    Duration queryTimeout = DatabaseConfig.DEFAULT_QUERY_TIMEOUT;
    Object rowMapper; // Can be RowMapperFactory or any RowMapper<?>

    /** Timeout for SQL queries. Default 30s. */
    public DatabaseBuilder queryTimeout(Duration timeout) {
        this.queryTimeout = timeout;
        return this;
    }

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

    /** Set all values from a {@link DatabaseConfig} at once. */
    public DatabaseBuilder fromConfig(DatabaseConfig config) {
        this.url = config.url();
        this.username = config.username();
        this.password = config.password();
        this.extraProperties =
            config.extraProperties() != null
                ? config.extraProperties()
                : new Properties();
        this.maxSize = config.maxSize();
        this.minIdle = config.minIdle();
        this.connectionTimeout = config.connectionTimeout();
        this.maxLifetime = config.maxLifetime();
        this.maxIdleTime = config.maxIdleTime();
        this.cleanInterval = config.cleanInterval();
        this.healthCheckQuery = config.healthCheckQuery();
        this.healthCheckTimeout = config.healthCheckTimeout();
        this.queryTimeout = config.queryTimeout();
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
     *
     * @param mapper the row mapper factory (typically a {@code RowMapperFactory})
     */
    public DatabaseBuilder rowMapper(Object mapper) {
        this.rowMapper = mapper;
        return this;
    }

    /**
     * Builds the {@link Database}, immediately opening {@link #minIdle}
     * connections.
     */
    public Database build() {
        if (url == null || url.isBlank()) throw new IllegalStateException(
            "url is required"
        );
        if (!url.startsWith("jdbc:")) throw new IllegalStateException(
            "url must start with 'jdbc:'"
        );
        if (username == null) throw new IllegalStateException(
            "username is required"
        );
        if (password == null) throw new IllegalStateException(
            "password is required"
        );
        if (maxSize < 1 || maxSize > 1024) throw new IllegalStateException(
            "maxSize must be between 1 and 1024, got " + maxSize
        );
        if (minIdle < 0 || minIdle > maxSize) throw new IllegalStateException(
            "minIdle must be between 0 and maxSize"
        );
        if (
            connectionTimeout == null || connectionTimeout.toMillis() <= 0
        ) throw new IllegalStateException("connectionTimeout must be positive");
        if (
            maxLifetime == null || maxLifetime.toMillis() <= 0
        ) throw new IllegalStateException("maxLifetime must be positive");
        if (
            maxIdleTime == null || maxIdleTime.toMillis() <= 0
        ) throw new IllegalStateException("maxIdleTime must be positive");
        if (
            cleanInterval == null || cleanInterval.toMillis() <= 0
        ) throw new IllegalStateException("cleanInterval must be positive");
        // healthCheckQuery is optional — null means use Connection.isValid()
        if (
            healthCheckTimeout == null || healthCheckTimeout.toMillis() <= 0
        ) throw new IllegalStateException(
            "healthCheckTimeout must be positive"
        );
        if (
            queryTimeout == null || queryTimeout.toMillis() <= 0
        ) throw new IllegalStateException("queryTimeout must be positive");

        // Build JDBC connection properties
        var props = new Properties(extraProperties);
        props.setProperty("user", username);
        props.setProperty("password", password);

        var config = new PoolConfig(
            url,
            props,
            maxSize,
            minIdle,
            connectionTimeout,
            maxLifetime,
            maxIdleTime,
            cleanInterval,
            healthCheckQuery,
            healthCheckTimeout
        );

        var pool = new ConnectionPool(config);
        // Convert query timeout to seconds, rounding up to avoid truncating sub-second values
        // JDBC setQueryTimeout() accepts int seconds, so we must convert from Duration
        long queryTimeoutMillis = queryTimeout.toMillis();
        int qTimeout = (int) Math.max(1, (queryTimeoutMillis + 999) / 1000); // round up, minimum 1 second
        
        if (rowMapper != null) {
            // rowMapper can be a RowMapperFactory or any compatible mapper
            return new DatabaseImpl(pool, rowMapper, qTimeout);
        }
        return new DatabaseImpl(pool, RowMapperFactory.standalone(), qTimeout);
    }
}
