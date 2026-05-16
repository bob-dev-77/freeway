package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.*;
import com.jujin.freeway.ioc.annotations.PreventServiceDecoration;
import java.util.Map;

@PreventServiceDecoration
public class ServiceOverrideImpl implements ServiceOverride {

    private final Map<Class<?>, Object> configuration;

    public ServiceOverrideImpl(Map<Class<?>, Object> configuration) {
        this.configuration = configuration;
    }

    @Override
    public InjectionProvider getServiceOverrideProvider() {
        return new InjectionProvider() {
            @Override
            public <T> T provide(
                Class<T> objectType,
                AnnotationProvider annotationProvider,
                ServiceLocator locator
            ) {
                return objectType.cast(configuration.get(objectType));
            }
        };
    }
}
