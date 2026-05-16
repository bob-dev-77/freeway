package com.jujin.freeway.ioc;

import com.jujin.freeway.ioc.annotations.UsesMappedConfiguration;

/**
 * Used to override built in services. Simply contribute a mapping from a type
 * to an instance of that type. Anywhere that exact type is injected, without
 * specifying markers or other annotations, the contributed instance will be
 * injected, even if there is already a service that implements the interface.
 * <p>
 * In fact, this is <em>not</em> limited to overriding services; any object that
 * can be injected based solely on type can be contributed.
 *
 */
@UsesMappedConfiguration(key = Class.class, value = Object.class)
public interface ServiceOverride {
    /**
     * Returns a provider based on the configuration; this is wired into the
     * {@link InjectionResolver}'s configuration.
     */
    InjectionProvider getServiceOverrideProvider();
}
