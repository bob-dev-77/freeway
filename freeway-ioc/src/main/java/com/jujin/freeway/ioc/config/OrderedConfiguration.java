package com.jujin.freeway.ioc.config;

/**
 * Object passed into a service contributor method that allows the method
 * provide contributed values to the service's configuration.
 * <p>
 * A service can <em>collect</em> contributions in three different ways:
 * <ul>
 * <li>As an un-ordered collection of values</li>
 * <li>As an ordered list of values (where each value has a unique id,
 * pre-requisites and post-requisites)</li>
 * <li>As a map of keys and values
 * </ul>
 * <p>
 * The service defines the <em>type</em> of contribution, in terms of a base
 * class or service interface. Contributions must be compatible with the type,
 * or be {@linkplain com.jujin.freeway.ioc.coercion.TypeCoercer coercable} to
 * the type.
 *
 * @see com.jujin.freeway.ioc.annotations.Contribute
 * @see com.jujin.freeway.ioc.annotations.UsesConfiguration
 */
public interface OrderedConfiguration<T> {
    /**
     * Adds an ordered object to a service's contribution. Each object has an id
     * (which must be unique). Optionally, pre-requisites (a list of ids that must
     * precede this object) and post-requisites (ids that must follow) can be
     * provided.
     * <p>
     * If no constraints are supplied, then an implicit constraint is supplied:
     * after the previously contributed id <em>within the same contribution
     * method</em>.
     *
     * @param id
     *            a unique id for the object; the id will be fully qualified with
     *            the contributing module's id
     * @param constraints
     *            used to order the object relative to other contributed objects
     * @param object
     *            to add to the service's configuration
     */
    void add(String id, T object, String... constraints);

    /**
     * Overrides a normally contributed object. Each override must match a single
     * normally contributed object.
     *
     * @param id
     *            identifies object to override
     * @param object
     *            overriding object (may be null)
     * @param constraints
     *            constraints for the overridden object, replacing constraints for
     *            the original object (even if omitted, in which case the override
     *            object will have no ordering constraints)
     */
    void override(String id, T object, String... constraints);

    /**
     * Adds an ordered object by instantiating (with dependencies) the indicated
     * class. When the configuration type is an interface and the class to be
     * contributed is a local file, then a reloadable proxy for the class will be
     * created and contributed.
     *
     * @param id
     *            of contribution (used for ordering)
     * @param clazz
     *            class to instantiate
     * @param constraints
     *            used to order the object relative to other contributed objects
     */
    void addInstance(String id, Class<? extends T> clazz, String... constraints);

    /**
     * Instantiates an object and adds it as an override. When the configuration
     * type is an interface and the class to be contributed is a local file, then a
     * reloadable proxy for the class will be created and contributed.
     *
     * @param id
     *            of object to override
     * @param clazz
     *            to instantiate
     * @param constraints
     *            constraints for the overridden object, replacing constraints for
     *            the original object (even if omitted, in which case the override
     *            object will have no ordering constraints)
     */
    void overrideInstance(String id, Class<? extends T> clazz, String... constraints);
}
