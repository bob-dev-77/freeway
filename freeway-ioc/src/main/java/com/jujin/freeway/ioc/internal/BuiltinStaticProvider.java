package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.AnnotationProvider;
import com.jujin.freeway.ioc.InjectionProvider;
import com.jujin.freeway.ioc.ServiceLocator;

/**
 * Provides a single object of a given type.
 *
 */
public class BuiltinStaticProvider<S> implements InjectionProvider {

    private final Class<S> valueType;

    private final S value;

    public BuiltinStaticProvider(Class<S> valueType, S value) {
        this.valueType = valueType;
        this.value = value;

        assert valueType != null;
        assert value != null;
    }

    @Override
    public <T> T provide(
        Class<T> objectType,
        AnnotationProvider annotationProvider,
        ServiceLocator locator
    ) {
        if (objectType == valueType) {
            return objectType.cast(value);
        }

        return null;
    }
}
