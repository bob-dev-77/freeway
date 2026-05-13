package com.jujin.freeway.db;

import com.jujin.freeway.db.internal.MigrationRunner;
import com.jujin.freeway.ioc.classpath.ClassPathScanner;
import com.jujin.freeway.ioc.internal.ClassPathScannerImpl;
import com.jujin.freeway.ioc.internal.ClassPathURLConverterImpl;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MigrationRunnerTest {

    private static final Logger logger = LoggerFactory.getLogger(MigrationRunnerTest.class);

    record TestItem(long id, String label) {}

    @Test
    void shouldRunMigrationsInOrder() throws Exception {
        var db = new DatabaseBuilder()
            .url("jdbc:h2:mem:" + System.nanoTime())
            .username("sa")
            .password("")
            .maxSize(5)
            .build();

        try {
            ClassPathScanner scanner = new ClassPathScannerImpl(
                new ClassPathURLConverterImpl());

            // Run migrations
            var runner = new MigrationRunner(db, scanner, "db/migration/", logger);
            runner.run();

            // Verify V001 created the table and V002 inserted data
            List<TestItem> items = db.sql("SELECT id, label FROM test_items ORDER BY id")
                .list(TestItem.class);
            assertEquals(2, items.size());
            assertEquals("migration-test", items.get(0).label());
            assertEquals("second-item", items.get(1).label());

            // Verify _migrations records exist
            List<String> versions = db.sql("SELECT version FROM _migrations ORDER BY version")
                .list(String.class);
            assertEquals(3, versions.size());
            assertEquals("V001", versions.get(0));
            assertEquals("V002", versions.get(1));
            assertEquals("V003", versions.get(2));

            // Running again should be idempotent
            runner.run();
            List<String> versions2 = db.sql("SELECT version FROM _migrations ORDER BY version")
                .list(String.class);
            assertEquals(3, versions2.size());
        } finally {
            db.close();
        }
    }

    @Test
    void shouldHandleEmptyMigrationPath() throws Exception {
        var db = new DatabaseBuilder()
            .url("jdbc:h2:mem:" + System.nanoTime())
            .username("sa")
            .password("")
            .maxSize(5)
            .build();

        try {
            ClassPathScanner scanner = new ClassPathScannerImpl(
                new ClassPathURLConverterImpl());

            // Path with no SQL files
            var runner = new MigrationRunner(db, scanner, "db/nonexistent/", logger);
            runner.run(); // Should not throw

            // _migrations table should exist even if empty
            db.sql("SELECT version FROM _migrations").list(String.class);
        } finally {
            db.close();
        }
    }

    @Test
    void versionFromPath() {
        assertEquals("V001", MigrationRunner.versionFromPath("db/migration/V001__foo.sql"));
        assertEquals("V002", MigrationRunner.versionFromPath("V002__bar.sql"));
        assertEquals("abc", MigrationRunner.versionFromPath("abc.sql"));
        assertEquals("001", MigrationRunner.versionFromPath("db/001__desc.sql"));
    }
}
