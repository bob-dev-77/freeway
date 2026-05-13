package com.jujin.freeway.db.internal;

import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;

/**
 * A JDBC {@link Connection} with pool bookkeeping metadata.
 */
public class PooledConnection {

    private final Connection jdbcConnection;
    private final Instant createdAt;
    private volatile Instant lastReturned;

    PooledConnection(Connection jdbcConnection, Instant createdAt) {
        this.jdbcConnection = jdbcConnection;
        this.createdAt = createdAt;
        this.lastReturned = createdAt;
    }

    public Connection jdbcConnection() {
        return jdbcConnection;
    }

    void markReturned() {
        this.lastReturned = Instant.now();
    }

    boolean isExpired(Instant now, Duration maxLifetime, Duration maxIdleTime) {
        if (Duration.between(createdAt, now).compareTo(maxLifetime) > 0)
            return true;
        if (Duration.between(lastReturned, now).compareTo(maxIdleTime) > 0)
            return true;
        return false;
    }
}
