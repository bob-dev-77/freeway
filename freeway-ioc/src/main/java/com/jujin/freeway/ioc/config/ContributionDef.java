package com.jujin.freeway.ioc.config;

import com.jujin.freeway.ioc.Markable;
import com.jujin.freeway.ioc.ModuleBuilderSource;
import com.jujin.freeway.ioc.ServiceResources;

/**
 * Contribution to a service configuration.
 * <p>
 * The toString() method of the ContributionDef will be used for some exception
 * reporting and should clearly identify where the contribution comes from; the
 * normal behavior is to identify the class and method of the contribution
 * method.
 */
public interface ContributionDef extends Markable {
    /**
     * Identifies the service contributed to.
     */
    String getServiceId();

    /**
     * Performs the work needed to contribute into the standard, unordered
     * configuration.
     *
     * @param moduleSource
     *            the source, if needed, of the module instance associated with the
     *            contribution
     * @param resources
     *            allows access to services visible to the module
     * @param configuration
     *            the unordered configuration into which values should be loaded.
     *            This instance will encapsulate all related error checks (such as
     *            passing of nulls or inappropriate classes).
     */
    @SuppressWarnings("rawtypes")
    void contribute(
        ModuleBuilderSource moduleSource,
        ServiceResources resources,
        Configuration configuration);

    /**
     * Performs the work needed to contribute into the ordered configuration.
     *
     * @param moduleSource
     *            the source, if needed, of the module instance associated with the
     *            contribution
     * @param resources
     *            allows access to services visible to the module
     * @param configuration
     *            the ordered configuration into which values should be loaded. This
     *            instance will encapsulate all related error checks (such as
     *            passing of nulls or inappropriate classes).
     */
    @SuppressWarnings("rawtypes")
    void contribute(
        ModuleBuilderSource moduleSource,
        ServiceResources resources,
        OrderedConfiguration configuration);

    /**
     * Performs the work needed to contribute into the mapped configuration.
     *
     * @param moduleSource
     *            the source, if needed, of the module instance associated with the
     *            contribution
     * @param resources
     *            allows access to services visible to the module
     * @param configuration
     *            the mapped configuration into which values should be loaded. This
     *            instance will encapsulate all related error checks (such as
     *            passing of null keys or values or inappropriate classes, or
     *            duplicate keys).
     */
    @SuppressWarnings("rawtypes")
    void contribute(
        ModuleBuilderSource moduleSource,
        ServiceResources resources,
        MappedConfiguration configuration);

    /**
     * Is this contribution optional, meaning it is not an error if the service to
     * which the contribution is targetted does not exist.
     *
     * @return true if this contribution is optional
     */
    default boolean isOptional() {
        return false;
    }
}
