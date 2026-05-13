package com.jujin.freeway.ioc.property;
import com.jujin.freeway.ioc.*;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Organizes all {@link com.jujin.freeway.ioc.PropertyAdapter}s for a particular
 * class.
 * <p>
 * Only provides access to <em>simple</em> properties. Indexed properties are
 * ignored.
 * <p>
 * When accessing properties by name, the case of the name is ignored.
 */
public interface BeanPropertyAdapter {
    /**
     * Returns the names of all properties, sorted into alphabetic order. This
     * includes true properties (as defined in the JavaBeans specification), but
     * also public fields. Starting in Freeway 5.3, even public static fields are
     * included.
     *
     * @return the property names.
     */
    List<String> getPropertyNames();

    /**
     * Returns the type of bean this adapter provides properties for.
     *
     * @return the type of the bean.
     */
    @SuppressWarnings("rawtypes")
    Class getBeanType();

    /**
     * Returns the property adapter with the given name, or null if no such adapter
     * exists.
     *
     * @param name
     *            of the property (case is ignored)
     * @return the PropertyAdapter instance associated with that property
     */
    PropertyAdapter getPropertyAdapter(String name);

    /**
     * Reads the value of a property.
     *
     * @param instance
     *            the object to read a value from
     * @param propertyName
     *            the name of the property to read (case is ignored)
     * @return the value
     * @throws UnsupportedOperationException
     *             if the property is write only
     * @throws IllegalArgumentException
     *             if property does not exist
     */
    Object get(Object instance, String propertyName);

    /**
     * Updates the value of a property.
     *
     * @param instance
     *            the object to update
     * @param propertyName
     *            the name of the property to update (case is ignored)
     * @param value
     *            the value to be set
     * @throws UnsupportedOperationException
     *             if the property is read only
     * @throws IllegalArgumentException
     *             if property does not exist
     */
    void set(Object instance, String propertyName, Object value);

    /**
     * Returns the annotation of a given property for the specified type if such an
     * annotation is present, else null.
     *
     * @param instance
     *            the object to read a value from
     * @param propertyName
     *            the name of the property to read (case is ignored)
     * @param annotationClass
     *            the type of annotation to return
     * @return the Annotation instance
     * @throws IllegalArgumentException
     *             if property does not exist
     */
    Annotation getAnnotation(Object instance, String propertyName, Class<? extends Annotation> annotationClass);
}
