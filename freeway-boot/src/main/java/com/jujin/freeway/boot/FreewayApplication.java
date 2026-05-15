package com.jujin.freeway.boot;

import com.jujin.freeway.boot.config.*;
import com.jujin.freeway.ioc.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

/**
 * Entry point for bootstrapping a freeway-boot application.
 * <p>
 * Loads external configuration (CLI args, JSON, properties, env vars), injects
 * it into the IoC {@link Registry} via {@link BootModuleDefinition}, discovers
 * SPI modules via {@code autoDiscover()}, then starts the container.
 * <p>
 * Usage:
 *
 * <pre>{@code
 * public class MyApp {
 *     public static void main(String[] args) {
 *         FreewayApp app = FreewayApplication.run(MyApp.class, args);
 *     }
 * }
 * }</pre>
 */
public class FreewayApplication {

    private static final Logger LOG = LoggerFactory.getLogger(FreewayApplication.class);

    private final Class<?> primarySource;
    private final String[] args;

    private FreewayApplication(Class<?> primarySource, String... args) {
        this.primarySource = primarySource;
        this.args = args;
    }

    /**
     * Static factory: creates and runs a freeway-boot application.
     *
     * @param primarySource
     *            the primary application class (can contain bind() / build*()
     *            methods as an IoC module)
     * @param args
     *            command-line arguments
     * @return a running {@link FreewayApp} handle
     */
    public static FreewayApp run(Class<?> primarySource, String... args) {
        return new FreewayApplication(primarySource, args).run();
    }

    private FreewayApp run() {
        // 1. Load external configuration (CLI, YAML, properties, environment)
        Map<String, String> config = loadConfig();

        // 2. Build the configuration module and feed it into the Registry.Builder
        BootModuleDefinition bootConfigModule =
            new BootModuleDefinition(config);

        Registry.Builder builder = new Registry.Builder();

        // Add the boot config module (bridges external config into the SymbolSource)
        builder.add(bootConfigModule);

        // Add the user's primary application class as an IoC module
        builder.add(primarySource.getName());

        // Discover all SPI-registered modules via ServiceLoader<ModuleProvider>
        builder.autoDiscover();

        // 3. Build the Registry
        Registry registry = builder.build();

        // 4. Perform startup (eager loads, RegistryStartup Runnables)
        registry.performRegistryStartup();

        // 5. Return app handle with JVM shutdown hook
        FreewayApp app = new FreewayAppImpl(registry);
        Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown));

        return app;
    }

    private Map<String, String> loadConfig() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        var configSources = new com.jujin.freeway.boot.config.ConfigSource();

        // Priority 100: Command-line arguments (--key=value)
        configSources.add(new CommandLineArgsProvider(args));

        // Priority 300: Application JSON (classpath:application.json)
        var jsonResource = cl.getResourceAsStream("application.json");
        if (jsonResource != null) {
            configSources.add(new JsonConfigProvider(jsonResource, 300));
        }

        // Priority 400: Application properties (classpath:application.properties)
        var propsResource = cl.getResourceAsStream("application.properties");
        if (propsResource != null) {
            configSources.add(new PropertiesConfigProvider(propsResource, 400));
        }

        // Priority 500: Environment variables (prefixed with FREEWAY_)
        configSources.add(new EnvironmentConfigProvider());

        // Merge all sources into a single map (higher priority overrides lower)
        Map<String, String> config = configSources.merge();
        
        // Debug: log loaded configuration keys
        if (LOG.isDebugEnabled()) {
            LOG.debug("Loaded {} configuration keys: {}", config.size(), config.keySet());
        }
        
        return config;
    }
}
