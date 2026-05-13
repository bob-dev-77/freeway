package com.jujin.freeway.test.advisor;

import com.jujin.freeway.ioc.Registry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests proving that @Advise alone can fully replace @Decorate. All test
 * assertions are identical to those in {@link AdvisorTest}, but the module
 * uses stacked MethodAdvice entries instead of a Decorator wrapper.
 */
class AdvisorOnlyTest {

    private Registry registry;

    @AfterEach
    void tearDown() {
        if (registry != null) {
            registry.shutdown();
        }
    }

    @Test
    @DisplayName("Advisor-only: bracket wrapping + uppercase on greet")
    void testBracketWrapAndUpperCaseOnGreet() {
        registry = Registry.Builder.startAndBuild(AdvisorOnlyTestModule.class);
        Greeter greeter = registry.getService("Greeter", Greeter.class);
        assertEquals("[Hello, WORLD]", greeter.greet("world"));
    }

    @Test
    @DisplayName("Advisor-only: bracket wrapping + uppercase on farewell")
    void testBracketWrapAndUpperCaseOnFarewell() {
        registry = Registry.Builder.startAndBuild(AdvisorOnlyTestModule.class);
        Greeter greeter = registry.getService("Greeter", Greeter.class);
        assertEquals("[Goodbye, WORLD]", greeter.farewell("world"));
    }

    @Test
    @DisplayName("Advisor-only: chain operates correctly on greet('Java')")
    void testChainOnGreetJava() {
        registry = Registry.Builder.startAndBuild(AdvisorOnlyTestModule.class);
        Greeter greeter = registry.getService("Greeter", Greeter.class);
        assertEquals("[Hello, JAVA]", greeter.greet("Java"));
    }

    @Test
    @DisplayName("Advisor-only: unadvised method countChars passes through")
    void testUnadvisedMethodPassThrough() {
        registry = Registry.Builder.startAndBuild(AdvisorOnlyTestModule.class);
        Greeter greeter = registry.getService("Greeter", Greeter.class);
        assertEquals(5, greeter.countChars("hello"));
    }

    @Test
    @DisplayName("Advisor-only: bracket wrapping + uppercase with empty name")
    void testBracketWrapAndUpperCaseWithEmptyName() {
        registry = Registry.Builder.startAndBuild(AdvisorOnlyTestModule.class);
        Greeter greeter = registry.getService("Greeter", Greeter.class);
        assertEquals("[Goodbye, ]", greeter.farewell(""));
    }

    @Test
    @DisplayName("Advisor-only: default interface method works through proxy")
    void testDefaultMethodThroughProxy() {
        registry = Registry.Builder.startAndBuild(AdvisorOnlyTestModule.class);
        Greeter greeter = registry.getService("Greeter", Greeter.class);
        assertEquals("Default: Hello, Claude", greeter.greetWithDefault("Claude"));
    }
}
