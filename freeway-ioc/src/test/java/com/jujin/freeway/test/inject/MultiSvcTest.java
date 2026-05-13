package com.jujin.freeway.test.inject;

import static org.junit.jupiter.api.Assertions.*;

import com.jujin.freeway.ioc.Registry;
import java.util.Collection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for multiple services implementing the same interface: {@code @Primary}
 * disambiguation and {@code getServices()}.
 */
class MultiSvcTest {

    private Registry registry;

    @AfterEach
    void tearDown() {
        if (registry != null) {
            registry.shutdown();
        }
    }

    @Test
    @DisplayName("getService returns @Primary implementation when multiple exist")
    void testGetServiceReturnsPrimary() {
        registry = Registry.Builder.startAndBuild(MultiSvcModule.class);

        MultiSvc svc = registry.getService(MultiSvc.class);
        assertEquals("primary", svc.name());
    }

    @Test
    @DisplayName("getServices returns all implementations of the interface")
    void testGetServicesReturnsAll() {
        registry = Registry.Builder.startAndBuild(MultiSvcModule.class);

        Collection<MultiSvc> all = registry.getServices(MultiSvc.class);
        assertEquals(2, all.size());

        var names = all.stream().map(MultiSvc::name).sorted().toList();
        assertEquals("primary", names.get(0));
        assertEquals("secondary", names.get(1));
    }

    @Test
    @DisplayName("getService by id still works with multiple implementations")
    void testGetServiceById() {
        registry = Registry.Builder.startAndBuild(MultiSvcModule.class);

        MultiSvc primary = registry.getService(
            "PrimaryMultiSvc",
            MultiSvc.class);
        assertEquals("primary", primary.name());

        MultiSvc secondary = registry.getService(
            "SecondaryMultiSvc",
            MultiSvc.class);
        assertEquals("secondary", secondary.name());
    }

    @Test
    @DisplayName("getServices returns empty collection when no services match")
    void testGetServicesEmptyForUnmatchedInterface() {
        registry = Registry.Builder.startAndBuild(MultiSvcModule.class);

        // Appendable is not bound by any module — should return empty
        Collection<Appendable> all = registry.getServices(Appendable.class);
        assertTrue(all.isEmpty());
    }
}
