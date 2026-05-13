package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.AnnotationProvider;
import com.jujin.freeway.ioc.ServiceLocator;
import com.jujin.freeway.ioc.ServiceOverride;
import com.jujin.freeway.ioc.ServiceProvider;
import com.jujin.freeway.ioc.annotations.PreventServiceDecoration;

import java.util.Map;

@PreventServiceDecoration
public class ServiceOverrideImpl implements ServiceOverride {

    private final Map<Class<?>, Object> configuration;

    public ServiceOverrideImpl(Map<Class<?>, Object> configuration) {
        this.configuration = configuration;
    }

    @Override
    public ServiceProvider getServiceOverrideProvider() {
        return new ServiceProvider() {
            @Override
            public <T> T resolve(
                Class<T> objectType,
                AnnotationProvider annotationProvider,
                ServiceLocator locator) {
                return objectType.cast(configuration.get(objectType));
            }
        };
    }
}
