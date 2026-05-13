package com.jujin.freeway.ioc.property;

import java.lang.annotation.Annotation;

/**
 * A wrapper around the JavaBean Introspector that allows more manageable access
 * to JavaBean properties of objects.
 * <p>
 * Only provides access to <em>simple</em> properties. Indexed properties are
 * ignored.
 * <p>
 * public fields can now be accessed as if they were
 * properly JavaBean properties. Where there is a name conflict, the true
 * property will be favored over the field access.
 */
public interface PropertyAccess {
    /**
     * Reads the value of a property.
     *
     * @throws UnsupportedOperationException
     *             if the property is write only
     * @throws IllegalArgumentException
     *             if property does not exist
     */
    Object get(Object instance, String propertyName);

    /**
     * Updates the value of a property.
     *
     * @throws UnsupportedOperationException
     *             if the property is read only
     * @throws IllegalArgumentException
     *             if property does not exist
     */
    void set(Object instance, String propertyName, Object value);

    /**
     * Returns the annotation of a given property for the specified type if such an
     * annotation is present, else null. A convenience over invoking
     * {@link #getAdapter(Object)}.{@link BeanPropertyAdapter#getPropertyAdapter(String)}.{@link PropertyAdapter#getAnnotation(Class)}
     *
     * @param instance
     *            the object to read a value from
     * @param propertyName
     *            the name of the property to read (case is ignored)
     * @param annotationClass
     *            the type of annotation to return
     * @throws IllegalArgumentException
     *             if property does not exist
     */
    Annotation getAnnotation(
        Object instance,
        String propertyName,
        Class<? extends Annotation> annotationClass);

    /**
     * Returns the adapter for a particular object instance. A convienience over
     * invoking {@link #getAdapter(Class)}.
     */
    BeanPropertyAdapter getAdapter(Object instance);

    /**
     * Returns the adapter used to access properties within the indicated class.
     */
    BeanPropertyAdapter getAdapter(Class<?> forClass);

    /**
     * Discards all stored property access information, discarding all created class
     * adapters.
     */
    void clearCache();
}
