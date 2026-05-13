package com.jujin.freeway.ioc.lifecycle;

import com.jujin.freeway.ioc.annotations.UsesMappedConfiguration;

/**
 * Provides access to user defined lifecycles (beyond the two built-in
 * lifecycles: "singleton" and "primitive"). The user defined lifecycles are
 * contributed into the service's configuration.
 * <p>
 * Note that the default scope
 * {@linkplain com.jujin.freeway.ioc.internal.util.InternalUtils#DEFAULT
 * "singleton"} is special and not a contribution.
 */
@UsesMappedConfiguration(ServiceLifecycle.class)
public interface ServiceLifecycleSource {
    /**
     * Used to locate a configuration lifecycle, by name.
     *
     * @param scope
     * @return the named lifecycle, or null if the name is not found
     */
    ServiceLifecycle get(String scope);
}
