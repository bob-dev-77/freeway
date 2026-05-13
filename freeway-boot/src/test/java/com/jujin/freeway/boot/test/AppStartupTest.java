package com.jujin.freeway.boot.test;

import com.jujin.freeway.boot.FreewayApp;
import com.jujin.freeway.boot.FreewayApplication;
import com.jujin.freeway.ioc.Registry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests basic application startup and shutdown.
 */
class AppStartupTest {

    @Test
    @DisplayName("application starts and returns non-null handle")
    void testAppStartup() {
        FreewayApp app = FreewayApplication.run(TestBootApp.class);
        assertNotNull(app, "app handle must not be null");
        app.shutdown();
    }

    @Test
    @DisplayName("registry is accessible from the app handle")
    void testRegistryAccessible() {
        FreewayApp app = FreewayApplication.run(TestBootApp.class);
        try {
            Registry registry = app.getRegistry();
            assertNotNull(registry, "registry must not be null");
        } finally {
            app.shutdown();
        }
    }

    @Test
    @DisplayName("shutdown does not throw")
    void testShutdown() {
        FreewayApp app = FreewayApplication.run(TestBootApp.class);
        assertDoesNotThrow(app::shutdown);
    }
}
