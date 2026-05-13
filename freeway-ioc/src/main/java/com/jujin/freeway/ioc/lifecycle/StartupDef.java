package com.jujin.freeway.ioc.lifecycle;

import com.jujin.freeway.ioc.ModuleBuilderSource;
import com.jujin.freeway.ioc.ServiceLocator;
import com.jujin.freeway.ioc.advisor.OperationTracker;
import org.slf4j.Logger;

/**
 * Represents a public module method (static or instance) with a
 * {@link com.jujin.freeway.ioc.annotations.Startup} annotation. Such methods
 * are invoked (possibly triggering side effects such as instantiating services
 * and proxies).
 *
 */
public interface StartupDef {
    /**
     * Invoke the startup method, which includes computing any parameters to the
     * method.
     */
    void invoke(ModuleBuilderSource moduleBuilderSource,
        OperationTracker tracker,
        ServiceLocator locator,
        Logger logger);
}
