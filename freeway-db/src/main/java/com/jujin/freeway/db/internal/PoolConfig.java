package com.jujin.freeway.db.internal;

import java.time.Duration;
import java.util.Properties;

/**
 * Immutable pool configuration. Created by {@link com.jujin.freeway.db.DatabaseBuilder}.
 */
public record PoolConfig(
    String url,
    Properties properties,
    int maxSize,
    int minIdle,
    Duration connectionTimeout,
    Duration maxLifetime,
    Duration maxIdleTime,
    Duration cleanInterval,
    String healthCheckQuery,
    Duration healthCheckTimeout) {
}
