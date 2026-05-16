package com.jujin.freeway.db.internal;

import com.jujin.freeway.db.Database;
import com.jujin.freeway.db.SqlException;
import com.jujin.freeway.ioc.scan.ClassPathScanner;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;

/**
 * Runs database migrations from SQL files on the classpath. Scans
 * {@code db/migration/} for {@code .sql} files sorted by name, executes
 * each one that hasn't been run yet inside a transaction, and records the
 * execution in a {@code _migrations} table.
 *
 * <p>
 * Migration file naming: {@code V001__description.sql}. Files are sorted
 * lexicographically by name, so a numeric prefix ensures correct ordering.
 *
 * <p>
 * Multi-instance safety: the {@code version} column in {@code _migrations}
 * has a unique constraint. If two instances try to run the same migration,
 * one INSERT will fail with a constraint violation and both will safely skip
 * it on next startup.
 *
 * <p>
 * <b>Note on transactional DDL:</b> Not all databases support transactional
 * DDL (e.g., MySQL MyISAM, Oracle). For databases that do (PostgreSQL, H2,
 * SQLite), migrations are fully atomic. For others, partial execution may
 * occur if a migration fails mid-way. Always test migrations on a copy of
 * production data first.
 */
public class MigrationRunner {

    private static final String DEFAULT_PATH = "db/migration/";
    private static final String TABLE = "_migrations";

    private final Database db;
    private final ClassPathScanner scanner;
    private final String migrationPath;
    private final Logger logger;

    public MigrationRunner(
        Database db,
        ClassPathScanner scanner,
        String migrationPath,
        Logger logger
    ) {
        this.db = db;
        this.scanner = scanner;
        this.migrationPath =
            migrationPath != null ? migrationPath : DEFAULT_PATH;
        this.logger = logger;
    }

    /** Run all pending migrations. */
    public void run() {
        ensureMigrationTable();

        List<String> files = scanMigrationFiles();
        if (files.isEmpty()) {
            logger.debug(
                "Freeway DB: no migration files found in {}",
                migrationPath
            );
            return;
        }

        Set<String> completed = completedVersions();

        int ran = 0;
        for (String file : files) {
            String version = versionFromPath(file);
            if (completed.contains(version)) continue;

            String sql = readFile(file);
            // Fail fast on blank migration files — this is likely a mistake
            if (sql.isBlank()) {
                throw new SqlException(
                    "Migration file is empty or contains only whitespace: " +
                        file
                );
            }

            runMigration(version, sql);
            ran++;
        }

        if (ran > 0) {
            logger.info("Freeway DB: {} migration(s) executed", ran);
        }
    }

    // ── Scan ────────────────────────────────────────────────────────

    private List<String> scanMigrationFiles() {
        try {
            Set<String> paths = scanner.scan(
                migrationPath.endsWith("/")
                    ? migrationPath
                    : migrationPath + "/",
                (packagePath, fileName) -> fileName.endsWith(".sql")
            );
            var sorted = new ArrayList<>(paths);
            sorted.sort(String::compareTo);
            return sorted;
        } catch (IOException e) {
            throw new SqlException(
                "Failed to scan migration path: " + migrationPath,
                e
            );
        }
    }

    // ── Database bookkeeping ─────────────────────────────────────────

    private void ensureMigrationTable() {
        db
            .sql(
                """
                CREATE TABLE IF NOT EXISTS %s (
                    version VARCHAR(255) PRIMARY KEY,
                    description VARCHAR(512),
                    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""".formatted(TABLE)
            )
            .execute();
    }

    private Set<String> completedVersions() {
        var versions = db
            .sql("SELECT version FROM " + TABLE)
            .list(String.class);
        return new HashSet<>(versions);
    }

    // ── Execution ────────────────────────────────────────────────────

    private void runMigration(String version, String sql) {
        db.transaction(tx -> {
            tx.sql(sql).execute();
            tx
                .sql("INSERT INTO " + TABLE + " (version) VALUES (?)", version)
                .execute();
        });
        logger.info("Freeway DB: migrated {}", version);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /** Read a classpath resource as a UTF-8 string. Fails if file is missing. */
    private String readFile(String path) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream in = cl.getResourceAsStream(path)) {
            if (in == null) {
                throw new SqlException(
                    "Migration file not found on classpath: " + path
                );
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SqlException("Failed to read migration file: " + path, e);
        }
    }

    /** Extract the version identifier from a path like "db/migration/V001__foo.sql". */
    public static String versionFromPath(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        // Strip trailing .sql
        if (name.endsWith(".sql")) name = name.substring(0, name.length() - 4);
        // If there's a __ separator, use the prefix as version; otherwise use full name
        int sep = name.indexOf("__");
        return sep > 0 ? name.substring(0, sep) : name;
    }
}
