package com.jujin.freeway.ioc.property;

import com.jujin.freeway.ioc.AnnotationProvider;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Provides access to a single property within a class. Acts as an
 * {@link com.jujin.freeway.ioc.AnnotationProvider}; when searching for
 * annotations, the read method (if present) is checked first, followed by the
 * write method, followed by the underlying field (when the property name
 * matches the field name).
 * <p>
 * Starting in release 5.2, this property may actually be a public field. In
 * 5.3, it may be a public static field.
 *
 * @see BeanPropertyAdapter
 */
public interface PropertyAdapter extends AnnotationProvider {
    /**
     * Returns the name of the property (or public field).
     */
    String getName();

    /**
     * Returns true if the property is readable (i.e., has a getter method or is a
     * public field).
     */
    boolean isRead();

    /**
     * Returns the method used to read the property, or null if the property is not
     * readable (or is a public field).
     */
    public Method getReadMethod();

    /**
     * Returns true if the property is writeable (i.e., has a setter method or is a
     * non-final field).
     */
    boolean isUpdate();

    /**
     * Returns the method used to update the property, or null if the property is
     * not writeable (or a public field).
     */
    public Method getWriteMethod();

    /**
     * Reads the property value.
     *
     * @param instance
     *            to read from
     * @throws UnsupportedOperationException
     *             if the property is write only
     */
    Object get(Object instance);

    /**
     * Updates the property value. The provided value must not be null if the
     * property type is primitive, and must otherwise be of the proper type.
     *
     * @param instance
     *            to update
     * @param value
     *            new value for the property
     * @throws UnsupportedOperationException
     *             if the property is read only
     */
    void set(Object instance, Object value);

    /**
     * Returns the type of the property.
     */
    Class<?> getType();

    /**
     * Returns true if the return type of the read method is not the same as the
     * property type. This can occur when the property has been defined using
     * generics, in which case, the method's type may be Object when the property
     * type is something more specific. This method is primarily used when
     * generating runtime code related to the property.
     */
    boolean isCastRequired();

    /**
     * Returns the {@link BeanPropertyAdapter} that provides
     * access to other properties defined by the same class.
     */
    BeanPropertyAdapter getClassAdapter();

    /**
     * Returns the type of bean to which this property belongs. This is the same as
     * {@link BeanPropertyAdapter#getBeanType()}.
     */
    Class<?> getBeanType();

    /**
     * Returns true if the property is actually a public field (possibly, a public
     * static field).
     *
     */
    boolean isField();

    /**
     * Returns the field if the property is a public field or null if the property
     * is accessed via the read method.
     *
     */
    Field getField();

    /**
     * The class in which the property (or public field) is defined.
     *
     */
    Class<?> getDeclaringClass();
}
