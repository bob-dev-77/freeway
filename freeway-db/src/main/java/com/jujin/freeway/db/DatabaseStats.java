package com.jujin.freeway.db;

/**
 * Snapshot of connection pool state at an instant in time.
 */
public record DatabaseStats(

    int activeConnections,
    int idleConnections,
    int totalConnections,
    int waitingThreads,
    int maxConnections) {

    /** Fraction of pool capacity in use, from 0.0 to 1.0. */
    public double utilization() {
        return maxConnections == 0 ? 0.0 : (double) activeConnections / maxConnections;
    }
}
