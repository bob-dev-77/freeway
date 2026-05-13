package com.jujin.freeway.db;

import com.jujin.freeway.db.internal.DbHubImpl;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DbHubTest {

    @Test
    void shouldLookupByName() {
        var db1 = new DatabaseBuilder()
            .url("jdbc:h2:mem:" + System.nanoTime())
            .username("sa").password("")
            .maxSize(2).minIdle(0)
            .build();
        var db2 = new DatabaseBuilder()
            .url("jdbc:h2:mem:" + System.nanoTime())
            .username("sa").password("")
            .maxSize(2).minIdle(0)
            .build();

        try {
            DbHub dbs = new DbHubImpl(Map.of("primary", db1, "replica", db2));

            assertSame(db1, dbs.get("primary"));
            assertSame(db2, dbs.get("replica"));
            assertNull(dbs.get("unknown"));

            assertEquals(2, dbs.all().size());
            assertTrue(dbs.all().containsKey("primary"));
            assertTrue(dbs.all().containsKey("replica"));
        } finally {
            db1.close();
            db2.close();
        }
    }

    @Test
    void shouldBeIndependentPools() {
        var db1 = new DatabaseBuilder()
            .url("jdbc:h2:mem:" + System.nanoTime())
            .username("sa").password("")
            .build();
        var db2 = new DatabaseBuilder()
            .url("jdbc:h2:mem:" + System.nanoTime())
            .username("sa").password("")
            .build();

        try {
            // Each database has its own H2 in-memory instance
            db1.sql("CREATE TABLE t1 (id INT PRIMARY KEY)").execute();
            db2.sql("CREATE TABLE t2 (id INT PRIMARY KEY)").execute();

            // Verify independence
            db1.sql("INSERT INTO t1 VALUES (1)").execute();
            db2.sql("INSERT INTO t2 VALUES (2)").execute();

            assertEquals(1, db1.sql("SELECT count(*) FROM t1").one(Long.class).orElse(0L));
            assertEquals(1, db2.sql("SELECT count(*) FROM t2").one(Long.class).orElse(0L));

            // t2 doesn't exist in db1
            assertThrows(SqlException.class, () ->
                db1.sql("SELECT count(*) FROM t2").one(Long.class));
        } finally {
            db1.close();
            db2.close();
        }
    }
}
