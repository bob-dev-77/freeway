package com.jujin.freeway.test.inject;

import com.jujin.freeway.ioc.Registry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * JUnit tests for IoC injection: constructor injection, field injection, and
 * circular reference resolution via the module's builder methods.
 */
class InjectTest {

    private Registry registry;

    @AfterEach
    void tearDown() {
        if (registry != null) {
            registry.shutdown();
        }
    }

    @Test
    @DisplayName("Constructor injection + builder method: TestService3 -> TestService2 -> TestService1")
    void testService3Greeting() {
        registry = Registry.Builder.startAndBuild(TestModule.class);

        TestService3 t3 = registry.getService(TestService3.class);
        String expected = "Hello World! TestService2 : I'm TestService-1 T1 Injected bye TestService 2";
        assertEquals(expected, t3.sayHelloWorld());
    }

    @Test
    @DisplayName("Field injection via @Inject: TestService2 -> TestService1")
    void testService2SayBye() {
        registry = Registry.Builder.startAndBuild(TestModule.class);

        TestService2 t2 = registry.getService(TestService2.class);
        String expected = "I'm TestService-1 T1 Injected bye TestService 2";
        assertEquals(expected, t2.sayBye());
    }

    @Test
    @DisplayName("Constructor injection chaining: TestService1 -> TestService3 -> TestService2 -> TestService1")
    void testService1GreetingTest3() {
        registry = Registry.Builder.startAndBuild(TestModule.class);

        TestService1 t1 = registry.getService(TestService1.class);
        String expected = "Hi TestService3Hello World! TestService2 : I'm TestService-1 T1 Injected bye TestService 2";
        assertEquals(expected, t1.greetingTest3());
    }

    @Test
    @DisplayName("Freeway @Inject without value performs type-based injection")
    void testFreewayInjectTypeBased() {
        registry = Registry.Builder.startAndBuild(TestModule.class);
        FreewayInjectService svc = registry.getService(
            FreewayInjectService.class);
        assertNotNull(svc.getGreeterName());
    }

    @Test
    @DisplayName("Freeway @Inject with serviceId performs named injection")
    void testFreewayInjectNamed() {
        registry = Registry.Builder.startAndBuild(TestModule.class);
        FreewayInjectService svc = registry.getService(
            FreewayInjectService.class);
        String greeting = svc.greet();
        assertNotNull(greeting);
        assertEquals("Hello Freeway!", greeting);
    }
}
