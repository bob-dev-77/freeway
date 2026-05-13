package com.jujin.freeway.boot.test;

import com.jujin.freeway.boot.FreewayApp;
import com.jujin.freeway.boot.FreewayApplication;
import com.jujin.freeway.ioc.Registry;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.symbol.SymbolSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

            assertEquals("Freeway Boot", symbolSource.valueForSymbol("app.name"));
            assertEquals("8080", symbolSource.valueForSymbol("server.port"));
            assertEquals("Standalone IoC container", symbolSource.valueForSymbol("app.description"));
            assertEquals("1.0.0", symbolSource.valueForSymbol("app.version"));
        } finally {
            app.shutdown();
        }
    }

    @Test
    @DisplayName("CLI args override YAML values")
    void testCliArgsOverrideYaml() {
        FreewayApp app = FreewayApplication.run(TestBootApp.class, "--app.name=Overridden");
        try {
            Registry registry = app.getRegistry();
            SymbolSource symbolSource = registry.getService(SymbolSource.class);
            assertNotNull(symbolSource);

            assertEquals("Overridden", symbolSource.valueForSymbol("app.name"),
                "CLI arg --app.name=Overridden should override YAML value");
            assertEquals("8080", symbolSource.valueForSymbol("server.port"),
                "Non-overridden YAML values should still be present");
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

            assertNotNull(symbolSource.valueForSymbol("app.name"));
            assertNotNull(symbolSource.valueForSymbol("app.version"));
            assertNotNull(symbolSource.valueForSymbol("app.description"));
            assertNotNull(symbolSource.valueForSymbol("server.port"));
            assertNotNull(symbolSource.valueForSymbol("server.host"));
        } finally {
            app.shutdown();
        }
    }
}
