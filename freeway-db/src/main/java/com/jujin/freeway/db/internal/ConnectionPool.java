package com.jujin.freeway.db.internal;

import com.jujin.freeway.db.DatabaseStats;
import com.jujin.freeway.db.SqlException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Semaphore-based JDBC connection pool. The pool IS the connection manager —
 * no external DataSource, no third-party library.
 *
 * <p>
 * A {@link Semaphore} gates total connection count (maxSize). Idle
 * connections live in a lock-free {@link ConcurrentLinkedDeque}. A
 * background virtual thread evicts expired connections and keeps minIdle
 * connections warm.
 *
 * <p>
 * Thread-safe. Designed to be shared as a singleton.
 */
public class ConnectionPool implements AutoCloseable {

    private final PoolConfig config;
    private final Semaphore semaphore;
    private final ConcurrentLinkedDeque<PooledConnection> idle;
    private final AtomicInteger total;
    private volatile boolean closed;

    public ConnectionPool(PoolConfig config) {
        this.config = config;
        this.semaphore = new Semaphore(config.maxSize());
        this.idle = new ConcurrentLinkedDeque<>();
        this.total = new AtomicInteger(0);
        this.closed = false;

        warmUp();
        startCleaner();
    }

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Borrow a connection, blocking up to connectionTimeout. Each call
     * acquires a semaphore permit; {@link #release(PooledConnection)}
     * releases it back.
     */
    public PooledConnection borrow() {
        ensureOpen();

        try {
            if (!semaphore.tryAcquire(config.connectionTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new SqlException(
                    "Connection pool exhausted — waited " + config.connectionTimeout().toSeconds()
                    + "s for a connection (maxSize=" + config.maxSize() + ")");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SqlException("Interrupted while waiting for a connection", e);
        }

        // We hold a permit — get a connection
        boolean success = false;
        try {
            PooledConnection conn = idle.pollFirst();
            if (conn != null) {
                if (isValid(conn)) {
                    success = true;
                    return conn;
                }
                destroy(conn);
            }

            conn = createConnection();
            total.incrementAndGet();
            success = true;
            return conn;
        } finally {
            if (!success) {
                semaphore.release(); // return the permit if we failed to deliver
            }
        }
    }

    /**
     * Return a healthy connection to the idle queue. Releases the semaphore
     * permit held by this connection.
     */
    public void release(PooledConnection conn) {
        if (conn == null) return;
        if (closed || !isAlive(conn)) {
            destroy(conn);
            semaphore.release();
            return;
        }
        conn.markReturned();
        idle.offerFirst(conn);
        semaphore.release();
    }

    /** Permanently destroy a connection (already accounts for permit). */
    public void evict(PooledConnection conn) {
        if (conn == null) return;
        destroy(conn);
        semaphore.release();
    }

    private void destroy(PooledConnection conn) {
        closePhysical(conn);
        total.decrementAndGet();
    }

    public DatabaseStats stats() {
        return new DatabaseStats(
            total.get() - idle.size(), // active
            idle.size(),               // idle
            total.get(),               // total
            semaphore.getQueueLength(), // waiting
            config.maxSize());
    }

    @Override
    public void close() {
        closed = true;
        PooledConnection conn;
        while ((conn = idle.pollFirst()) != null) {
            closePhysical(conn);
        }
    }

    // ── Internals ───────────────────────────────────────────────────

    private void warmUp() {
        for (int i = 0; i < config.minIdle(); i++) {
            if (!semaphore.tryAcquire()) break;
            try {
                var conn = createConnection();
                total.incrementAndGet();
                idle.offerFirst(conn);
                semaphore.release(); // idle connections don't hold permits
            } catch (Exception e) {
                semaphore.release(); // return permit on failure
                break;
            }
        }
    }

    private PooledConnection createConnection() {
        try {
            Connection jdbcConn;
            if (config.properties().containsKey("user")) {
                jdbcConn = DriverManager.getConnection(
                    config.url(), config.properties());
            } else {
                jdbcConn = DriverManager.getConnection(config.url());
            }
            jdbcConn.setAutoCommit(true);
            return new PooledConnection(jdbcConn, Instant.now());
        } catch (SQLException e) {
            throw new SqlException("Failed to create connection: " + e.getMessage(), e);
        }
    }

    private boolean isValid(PooledConnection conn) {
        return isAlive(conn) && healthCheck(conn);
    }

    private boolean isAlive(PooledConnection conn) {
        Instant now = Instant.now();
        return !conn.isExpired(now, config.maxLifetime(), config.maxIdleTime());
    }

    private boolean healthCheck(PooledConnection conn) {
        try {
            Connection jdbcConn = conn.jdbcConnection();
            if (!jdbcConn.isValid((int) config.healthCheckTimeout().toSeconds())) {
                return false;
            }
            // Also run the configured query if the driver supports it
            try (Statement stmt = jdbcConn.createStatement()) {
                stmt.setQueryTimeout((int) config.healthCheckTimeout().toSeconds());
                stmt.execute(config.healthCheckQuery());
                return true;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private void closePhysical(PooledConnection conn) {
        try {
            conn.jdbcConnection().close();
        } catch (SQLException ignored) {
        }
    }

    private void ensureOpen() {
        if (closed) throw new SqlException("Database is closed");
    }

    // ── Background cleaner ──────────────────────────────────────────

    private void startCleaner() {
        Thread.ofVirtual()
            .name("freeway-db-cleaner")
            .start(() -> {
                while (!closed) {
                    try {
                        Thread.sleep(config.cleanInterval().toMillis());
                        clean();
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
    }

    private void clean() {
        Instant now = Instant.now();

        // Evict expired connections from idle queue (idle connections
        // don't hold permits in the current model, so no semaphore change)
        var it = idle.iterator();
        while (it.hasNext()) {
            var conn = it.next();
            if (conn.isExpired(now, config.maxLifetime(), config.maxIdleTime())) {
                it.remove();
                closePhysical(conn);
                total.decrementAndGet();
            }
        }

        // Maintain minIdle: acquire permit to create, then release
        // because the connection goes straight to idle
        int needed = config.minIdle() - idle.size();
        for (int i = 0; i < needed; i++) {
            if (total.get() >= config.maxSize()) break;
            if (!semaphore.tryAcquire()) break;
            try {
                var conn = createConnection();
                total.incrementAndGet();
                idle.offerFirst(conn);
                semaphore.release();
            } catch (Exception e) {
                semaphore.release();
                break;
            }
        }
    }
}
