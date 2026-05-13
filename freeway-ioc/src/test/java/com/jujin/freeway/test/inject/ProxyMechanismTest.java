package com.jujin.freeway.test.inject;

import com.jujin.freeway.ioc.Registry;
import com.jujin.freeway.ioc.internal.ServiceProxyGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for proxy mechanism selection (jdk vs classfile).
 */
class ProxyMechanismTest {

    @AfterEach
    void tearDown() {
        System.clearProperty("freeway.proxy-mechanism");
        ServiceProxyGenerator.setMechanism(ServiceProxyGenerator.JDK);
    }

    @Test
    @DisplayName("Default mechanism is JDK")
    void testDefaultIsJdk() {
        assertEquals(
            ServiceProxyGenerator.JDK,
            ServiceProxyGenerator.getMechanism());
    }

    @Test
    @DisplayName("System property selects classfile mechanism")
    void testSystemPropertySelectsClassFile() {
        System.setProperty("freeway.proxy-mechanism", "classfile");
        // Force re-read: ServiceProxyGenerator reads in static init, so we need
        // to call setMechanism to simulate what happens at startup
        ServiceProxyGenerator.setMechanism(
            System.getProperty("freeway.proxy-mechanism"));
        assertEquals(
            ServiceProxyGenerator.CLASSFILE,
            ServiceProxyGenerator.getMechanism());
    }

    @Test
    @DisplayName("ClassFile proxy creates working service proxies")
    void testClassFileProxyWorksEndToEnd() {
        System.setProperty("freeway.proxy-mechanism", "classfile");
        ServiceProxyGenerator.setMechanism(ServiceProxyGenerator.CLASSFILE);

        Registry registry = Registry.Builder.startAndBuild(TestModule.class);
        try {
            TestService3 t3 = registry.getService(TestService3.class);
            String expected = "Hello World! TestService2 : I'm TestService-1 T1 Injected bye TestService 2";
            assertEquals(expected, t3.sayHelloWorld());
        } finally {
            registry.shutdown();
        }
    }

    @Test
    @DisplayName("JDK proxy creates working service proxies")
    void testJdkProxyWorksEndToEnd() {
        System.setProperty("freeway.proxy-mechanism", "jdk");
        ServiceProxyGenerator.setMechanism(ServiceProxyGenerator.JDK);

        Registry registry = Registry.Builder.startAndBuild(TestModule.class);
        try {
            TestService3 t3 = registry.getService(TestService3.class);
            String expected = "Hello World! TestService2 : I'm TestService-1 T1 Injected bye TestService 2";
            assertEquals(expected, t3.sayHelloWorld());
        } finally {
            registry.shutdown();
        }
    }
}
