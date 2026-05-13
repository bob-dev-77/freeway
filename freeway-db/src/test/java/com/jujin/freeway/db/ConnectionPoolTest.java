package com.jujin.freeway.db;

import com.jujin.freeway.db.internal.ConnectionPool;
import com.jujin.freeway.db.internal.PoolConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionPoolTest {

    private PoolConfig h2Config() {
        var props = new Properties();
        props.setProperty("user", "sa");
        props.setProperty("password", "");
        return new PoolConfig(
            "jdbc:h2:mem:testdb_" + System.nanoTime(),
            props, 5, 1,
            Duration.ofSeconds(10), Duration.ofMinutes(30),
            Duration.ofMinutes(10), Duration.ofMinutes(1),
            "SELECT 1", Duration.ofSeconds(5));
    }

    @Test
    void shouldBorrowAndRelease() {
        var pool = new ConnectionPool(h2Config());
        try {
            var conn = pool.borrow();
            assertNotNull(conn);
            assertNotNull(conn.jdbcConnection());
            pool.release(conn);
            assertEquals(1, pool.stats().totalConnections());
            assertEquals(1, pool.stats().idleConnections());
        } finally {
            pool.close();
        }
    }

    @Test
    void shouldReuseConnection() {
        var pool = new ConnectionPool(h2Config());
        try {
            var c1 = pool.borrow();
            pool.release(c1);
            var c2 = pool.borrow();
            pool.release(c2);
            // With minIdle=1, should only have created 1 connection
            assertEquals(1, pool.stats().totalConnections());
        } finally {
            pool.close();
        }
    }

    @Test
    void shouldNotExceedMaxSize() throws Exception {
        var config = new PoolConfig(
            "jdbc:h2:mem:testdb_" + System.nanoTime(),
            h2Config().properties(), 2, 0,
            Duration.ofMillis(500), Duration.ofMinutes(30),
            Duration.ofMinutes(10), Duration.ofMinutes(1),
            "SELECT 1", Duration.ofSeconds(5));
        var pool = new ConnectionPool(config);

        try {
            var c1 = pool.borrow();
            var c2 = pool.borrow();
            assertEquals(2, pool.stats().activeConnections());

            // Third borrow should time out (pool at max, nothing released)
            assertThrows(SqlException.class, () -> pool.borrow());

            pool.release(c1);
            pool.release(c2);
        } finally {
            pool.close();
        }
    }

    @Test
    void shouldHandleConcurrentBorrows() throws Exception {
        var pool = new ConnectionPool(h2Config());
        int threads = 10;
        int iterations = 100;
        var latch = new CountDownLatch(threads);
        var errors = new AtomicInteger(0);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < iterations; i++) {
                            var conn = pool.borrow();
                            try (var stmt = conn.jdbcConnection().createStatement()) {
                                stmt.execute("SELECT 1");
                            }
                            pool.release(conn);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        }

        assertEquals(0, errors.get());
    }

    @Test
    void shouldPing() throws Exception {
        var pool = new ConnectionPool(h2Config());
        try {
            var conn = pool.borrow();
            assertTrue(conn.jdbcConnection().isValid(5));
            pool.release(conn);
        } finally {
            pool.close();
        }
    }
}
