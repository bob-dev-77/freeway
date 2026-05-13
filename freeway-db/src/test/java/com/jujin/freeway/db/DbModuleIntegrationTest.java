package com.jujin.freeway.db;

import com.jujin.freeway.ioc.Registry;
import com.jujin.freeway.ioc.annotations.ApplicationDefaults;
import com.jujin.freeway.ioc.annotations.Contribute;
import com.jujin.freeway.ioc.annotations.Startup;
import com.jujin.freeway.ioc.config.MappedConfiguration;
import com.jujin.freeway.ioc.symbol.SymbolProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DbModuleIntegrationTest {

    record Person(long id, String name, int age) {}

    /** Provides test database config. Uses ApplicationDefaults to avoid
     *  key conflicts with DbModule's FactoryDefaults contributions. */
    public static class TestConfigModule {

        @Contribute(SymbolProvider.class)
        @ApplicationDefaults
        public static void config(MappedConfiguration<String, Object> cfg) {
            cfg.add("freeway.db.url", "jdbc:h2:mem:" + System.nanoTime());
            cfg.add("freeway.db.username", "sa");
            cfg.add("freeway.db.password", "");
        }

        @Startup
        public static void setup(Database db) {
            db.sql("CREATE TABLE person (id BIGINT PRIMARY KEY, name VARCHAR, age INT)").execute();
            db.sql("INSERT INTO person (id, name, age) VALUES (?, ?, ?)", 1, "Alice", 30).execute();
            db.sql("INSERT INTO person (id, name, age) VALUES (?, ?, ?)", 2, "Bob", 25).execute();
        }
    }

    @Test
    void shouldCreateDatabaseThroughIoC() {
        Registry registry = Registry.Builder.startAndBuild(
            DbModule.class, TestConfigModule.class);

        try {
            Database db = registry.getService(Database.class);
            assertNotNull(db);
            assertTrue(db.ping());

            // Verify row mapping works through IoC path (TypeCoercer + PropertyAccess)
            List<Person> people = db.sql("SELECT id, name, age FROM person ORDER BY id")
                .list(Person.class);
            assertEquals(2, people.size());
            assertEquals("Alice", people.get(0).name());

            // Verify stats
            var stats = db.stats();
            assertTrue(stats.totalConnections() >= 1);
        } finally {
            registry.shutdown();
        }
    }
}
