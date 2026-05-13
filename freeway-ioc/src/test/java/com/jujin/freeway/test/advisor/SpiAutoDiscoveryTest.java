package com.jujin.freeway.test.advisor;

import com.jujin.freeway.ioc.Registry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JUnit tests verifying that modules are auto-discovered via JDK
 * {@link java.util.ServiceLoader} SPI without any explicit module class
 * registration.
 *
 * <p>
 * The built-in {@code FreewayIOCModule} and the test {@code AdvisorOnlyTestModule}
 * are both discovered from their respective
 * {@code META-INF/services/com.jujin.freeway.ioc.ModuleProvider} files.
 * </p>
 */
class SpiAutoDiscoveryTest {

    private Registry registry;

    @AfterEach
    void tearDown() {
        if (registry != null) {
            registry.shutdown();
        }
    }

    @Test
    @DisplayName("SPI-discovered: bracket wrap wraps greeting result")
    void testSpiDecoratorWrapsGreeting() {
        registry = Registry.Builder.spiAndBuild();
        Greeter greeter = registry.getService("Greeter", Greeter.class);
        assertEquals("[Hello, SPI]", greeter.greet("spi"));
    }

    @Test
    @DisplayName("SPI-discovered: advice uppercases name in farewell")
    void testSpiAdviceUppercasesName() {
        registry = Registry.Builder.spiAndBuild();
        Greeter greeter = registry.getService("Greeter", Greeter.class);
        assertEquals("[Goodbye, SPI]", greeter.farewell("spi"));
    }

    @Test
    @DisplayName("SPI-discovered: default method works through proxy")
    void testSpiDefaultMethodWorks() {
        registry = Registry.Builder.spiAndBuild();
        Greeter greeter = registry.getService("Greeter", Greeter.class);
        assertEquals("Default: Hello, SPI", greeter.greetWithDefault("SPI"));
    }
}
