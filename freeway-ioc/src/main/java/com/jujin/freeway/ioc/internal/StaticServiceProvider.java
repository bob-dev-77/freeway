package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.AnnotationProvider;
import com.jujin.freeway.ioc.ServiceLocator;
import com.jujin.freeway.ioc.ServiceProvider;

/**
 * Provides a single object of a given type.
 *
 */
public class StaticServiceProvider<S> implements ServiceProvider {

    private final Class<S> valueType;

    private final S value;

    public StaticServiceProvider(Class<S> valueType, S value) {
        this.valueType = valueType;
        this.value = value;

        assert valueType != null;
        assert value != null;
    }

    @Override
    public <T> T resolve(
        Class<T> objectType,
        AnnotationProvider annotationProvider,
        ServiceLocator locator) {
        if (objectType == valueType) {
            return objectType.cast(value);
        }

        return null;
    }
}
