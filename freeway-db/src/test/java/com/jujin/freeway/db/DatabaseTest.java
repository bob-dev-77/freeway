package com.jujin.freeway.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {

    private Database db;

    record User(long id, String name, String email) {}

    @BeforeEach
    void setUp() {
        db = new DatabaseBuilder()
            .url("jdbc:h2:mem:" + System.nanoTime())
            .username("sa")
            .password("")
            .maxSize(5)
            .minIdle(1)
            .build();

        db.sql("CREATE TABLE users (id BIGINT PRIMARY KEY, name VARCHAR, email VARCHAR)").execute();
        db.sql("INSERT INTO users (id, name, email) VALUES (?, ?, ?)", 1, "Alice", "alice@example.com").execute();
        db.sql("INSERT INTO users (id, name, email) VALUES (?, ?, ?)", 2, "Bob", "bob@example.com").execute();
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void shouldExecuteInsert() {
        int rows = db.sql("INSERT INTO users (id, name, email) VALUES (?, ?, ?)",
            3, "Charlie", "charlie@example.com").execute();
        assertEquals(1, rows);
    }

    @Test
    void shouldQueryList() {
        List<User> users = db.sql("SELECT id, name, email FROM users ORDER BY id")
            .list(User.class);
        assertEquals(2, users.size());
        assertEquals("Alice", users.get(0).name());
        assertEquals("Bob", users.get(1).name());
    }

    @Test
    void shouldQueryOne() {
        Optional<User> user = db.sql("SELECT id, name, email FROM users WHERE id = ?", 1)
            .one(User.class);
        assertTrue(user.isPresent());
        assertEquals("Alice", user.get().name());
    }

    @Test
    void shouldReturnEmptyForMissingRow() {
        Optional<User> user = db.sql("SELECT id, name, email FROM users WHERE id = ?", 999)
            .one(User.class);
        assertTrue(user.isEmpty());
    }

    @Test
    void shouldUseColonNamedParameters() {
        List<User> users = db.sql("SELECT id, name, email FROM users WHERE name = :name")
            .param("name", "Bob")
            .list(User.class);
        assertEquals(1, users.size());
        assertEquals("Bob", users.get(0).name());
    }

    @Test
    void shouldUseHashNamedParameters() {
        // #name style — order-independent, self-documenting
        var users = db.sql(
            "INSERT INTO users (id, name, email) VALUES (#id, #name, #email)")
            .param("email", "charlie@example.com")
            .param("name", "Charlie")
            .param("id", 3)
            .execute();
        assertEquals(1, users);

        List<User> result = db.sql("SELECT id, name, email FROM users WHERE id = ?", 3)
            .list(User.class);
        assertEquals(1, result.size());
        assertEquals("Charlie", result.get(0).name());
        assertEquals("charlie@example.com", result.get(0).email());
    }

    @Test
    void shouldMixNamedParamsOrderFreely() {
        // Verify that param() call order doesn't matter
        db.sql("INSERT INTO users (id, name, email) VALUES (#a, #b, #c)")
            .param("c", "c@x.com")
            .param("a", 99)
            .param("b", "Beta")
            .execute();

        Optional<User> user = db.sql("SELECT * FROM users WHERE id = 99").one(User.class);
        assertTrue(user.isPresent());
        assertEquals("Beta", user.get().name());
    }

    @Test
    void shouldExecuteInTransaction() {
        db.transaction(tx -> {
            tx.sql("INSERT INTO users (id, name, email) VALUES (?, ?, ?)", 3, "Tx", "tx@x.com").execute();
            tx.sql("INSERT INTO users (id, name, email) VALUES (?, ?, ?)", 4, "Tx2", "tx2@x.com").execute();
        });

        var count = new AtomicLong();
        db.transaction(tx -> {
            var result = tx.sql("SELECT count(*) FROM users").one(Long.class);
            count.set(result.orElse(0L));
        });
        assertEquals(4, count.get());
    }

    @Test
    void shouldRollbackOnException() {
        try {
            db.transaction(tx -> {
                tx.sql("INSERT INTO users (id, name, email) VALUES (?, ?, ?)", 3, "Tx", "tx@x.com").execute();
                throw new RuntimeException("boom");
            });
        } catch (RuntimeException e) {
            assertEquals("boom", e.getMessage());
        }

        long count = db.sql("SELECT count(*) FROM users").one(Long.class).orElse(0L);
        assertEquals(2, count);
    }

    @Test
    void shouldPing() {
        assertTrue(db.ping());
    }

    @Test
    void shouldReportStats() {
        var stats = db.stats();
        assertEquals(5, stats.maxConnections());
        assertTrue(stats.totalConnections() >= 1);
        assertTrue(stats.activeConnections() >= 0);
    }

    @Test
    void shouldMapSimpleType() {
        Long count = db.sql("SELECT count(*) FROM users").one(Long.class).orElse(0L);
        assertEquals(2, count);
    }

    @Test
    void shouldUseManualTransaction() throws Exception {
        try (var tx = db.beginTransaction()) {
            tx.sql("INSERT INTO users (id, name, email) VALUES (?, ?, ?)", 5, "Manual", "m@x.com").execute();
            tx.commit();
        }
        long count = db.sql("SELECT count(*) FROM users").one(Long.class).orElse(0L);
        assertEquals(3, count);
    }

    @Test
    void shouldRollbackUncommittedManualTransaction() throws Exception {
        try (var tx = db.beginTransaction()) {
            tx.sql("INSERT INTO users (id, name, email) VALUES (?, ?, ?)", 5, "Manual", "m@x.com").execute();
            // no commit → auto rollback on close
        }
        long count = db.sql("SELECT count(*) FROM users").one(Long.class).orElse(0L);
        assertEquals(2, count);
    }

    @Test
    void shouldExecutePositionalBatch() {
        int[] rows = db.batch("INSERT INTO users (id, name, email) VALUES (?, ?, ?)")
            .params(
                new Object[]{3, "Charlie", "c@x.com"},
                new Object[]{4, "Dana", "d@x.com"},
                new Object[]{5, "Evan", "e@x.com"})
            .execute();
        assertEquals(3, rows.length);
        assertEquals(1, rows[0]);
        assertEquals(1, rows[1]);
        assertEquals(1, rows[2]);

        long count = db.sql("SELECT count(*) FROM users").one(Long.class).orElse(0L);
        assertEquals(5, count);
    }

    @Test
    void shouldExecuteNamedBatch() {
        int[] rows = db.batch("INSERT INTO users (id, name, email) VALUES (#id, #name, #email)")
            .paramList(List.of(
                Map.of("id", 6, "name", "Fay", "email", "f@x.com"),
                Map.of("id", 7, "name", "Gus", "email", "g@x.com")))
            .execute();
        assertEquals(2, rows.length);
        assertEquals(1, rows[0]);
        assertEquals(1, rows[1]);
    }

    @Test
    void shouldExecuteBatchInTransaction() {
        db.transaction(tx -> {
            tx.batch("INSERT INTO users (id, name, email) VALUES (?, ?, ?)")
                .params(
                    new Object[]{8, "TxBatch1", "t1@x.com"},
                    new Object[]{9, "TxBatch2", "t2@x.com"})
                .execute();
        });

        long count = db.sql("SELECT count(*) FROM users").one(Long.class).orElse(0L);
        assertEquals(4, count);
    }
}
