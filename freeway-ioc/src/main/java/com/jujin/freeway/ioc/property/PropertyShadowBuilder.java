package com.jujin.freeway.ioc.property;
import com.jujin.freeway.ioc.*;

/**
 * Creates a "shadow" of a property of an object. The shadow has the same type
 * as the property, and delegates all method invocations to the property. Each
 * method invocation on the shadow re-acquires the value of the property from
 * the underlying object and delegates to the current value of the property.
 * <p>
 * Typically, the object in question is another service, one with the
 * "perthread" service lifecycle. This allows a global singleton to shadow a
 * value that is specific to the current thread (and therefore, the current
 * request).
 */
public interface PropertyShadowBuilder {
    /**
     * @param <T>
     * @param source
     *            the object from which a property will be extracted
     * @param propertyName
     *            the name of a property of the object, which must be readable
     * @param propertyType
     *            the expected type of the property, the actual property type must
     *            be assignable to this type
     * @return the shadow
     */
    <T> T build(Object source, String propertyName, Class<T> propertyType);
}
