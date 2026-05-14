package com.jujin.freeway.boot.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jujin.freeway.boot.FreewayApp;
import com.jujin.freeway.boot.FreewayApplication;
import com.jujin.freeway.ioc.Registry;
import com.jujin.freeway.ioc.symbol.SymbolSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests configuration injection via SymbolSource.
 */
class ConfigInjectionTest {

    @Test
    @DisplayName("application.yml values are bridged into SymbolSource")
    void testYamlValuesInSymbolSource() {
        FreewayApp app = FreewayApplication.run(TestBootApp.class);
        try {
            Registry registry = app.getRegistry();
            SymbolSource symbolSource = registry.getService(SymbolSource.class);
            assertNotNull(symbolSource, "SymbolSource must be available");

            assertEquals("Freeway Boot", symbolSource.resolve("app.name"));
            assertEquals("8080", symbolSource.resolve("server.port"));
            assertEquals(
                "Standalone IoC container",
                symbolSource.resolve("app.description")
            );
            assertEquals("1.0.0", symbolSource.resolve("app.version"));
        } finally {
            app.shutdown();
        }
    }

    @Test
    @DisplayName("CLI args override YAML values")
    void testCliArgsOverrideYaml() {
        FreewayApp app = FreewayApplication.run(
            TestBootApp.class,
            "--app.name=Overridden"
        );
        try {
            Registry registry = app.getRegistry();
            SymbolSource symbolSource = registry.getService(SymbolSource.class);
            assertNotNull(symbolSource);

            assertEquals(
                "Overridden",
                symbolSource.resolve("app.name"),
                "CLI arg --app.name=Overridden should override YAML value"
            );
            assertEquals(
                "8080",
                symbolSource.resolve("server.port"),
                "Non-overridden YAML values should still be present"
            );
        } finally {
            app.shutdown();
        }
    }

    @Test
    @DisplayName("all expected config keys are present")
    void testAllExpectedKeys() {
        FreewayApp app = FreewayApplication.run(TestBootApp.class);
        try {
            Registry registry = app.getRegistry();
            SymbolSource symbolSource = registry.getService(SymbolSource.class);
            assertNotNull(symbolSource);

            assertNotNull(symbolSource.resolve("app.name"));
            assertNotNull(symbolSource.resolve("app.version"));
            assertNotNull(symbolSource.resolve("app.description"));
            assertNotNull(symbolSource.resolve("server.port"));
            assertNotNull(symbolSource.resolve("server.host"));
        } finally {
            app.shutdown();
        }
    }
}
