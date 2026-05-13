package com.jujin.freeway.boot.test;

import com.jujin.freeway.boot.FreewayApp;
import com.jujin.freeway.boot.FreewayApplication;
import com.jujin.freeway.boot.test.services.Greeter;
import com.jujin.freeway.boot.test.services.Store;
import com.jujin.freeway.ioc.Registry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests service injection via the IoC container.
 */
class ServiceInjectionTest {

    @Test
    @DisplayName("simple service (Greeter) is resolvable and functional")
    void testGreeterService() {
        FreewayApp app = FreewayApplication.run(TestBootApp.class);
        try {
            Registry registry = app.getRegistry();
            Greeter greeter = registry.getService(Greeter.class);
            assertNotNull(greeter, "Greeter service must not be null");
            assertEquals("Hello, World!", greeter.greet("World"));
        } finally {
            app.shutdown();
        }
    }

    @Test
    @DisplayName("service with @Inject constructor (Store -> Greeter) is properly wired")
    void testStoreServiceWithInjection() {
        FreewayApp app = FreewayApplication.run(TestBootApp.class);
        try {
            Registry registry = app.getRegistry();
            Store store = registry.getService(Store.class);
            assertNotNull(store, "Store service must not be null");

            store.put("user1", "Alice");
            assertEquals("Hello, Alice!", store.get("user1"));
        } finally {
            app.shutdown();
        }
    }

    @Test
    @DisplayName("multiple entries via Store with delegated Greeter")
    void testStoreMultipleEntries() {
        FreewayApp app = FreewayApplication.run(TestBootApp.class);
        try {
            Registry registry = app.getRegistry();
            Store store = registry.getService(Store.class);
            assertNotNull(store);

            store.put("user1", "Alice");
            store.put("user2", "Bob");
            store.put("user3", "Charlie");

            assertEquals(3, store.snapshot().size());
            assertEquals("Hello, Alice!", store.get("user1"));
            assertEquals("Hello, Bob!", store.get("user2"));
            assertEquals("Hello, Charlie!", store.get("user3"));
        } finally {
            app.shutdown();
        }
    }
}
