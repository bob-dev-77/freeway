package com.jujin.freeway.ioc.internal.util;

import java.lang.reflect.Type;

/**
 * Used when invoking a contribute method to guard against a request for the
 * wrong type of configuration interface.
 */
public class WrongConfigurationTypeGuard implements InjectionContext {

    private final String serviceId;

    private final Class<?> guardType;

    private final Class<?> expectedType;

    public WrongConfigurationTypeGuard(
        String serviceId,
        Class<?> guardType,
        Class<?> expectedType) {
        this.serviceId = serviceId;
        this.guardType = guardType;
        this.expectedType = expectedType;
    }

    @Override
    public <T> T findResource(Class<T> type, Type genericType) {
        if (type == guardType)
            throw new IllegalArgumentException(
                String.format(
                    "Service '%s' is configured using %s, not %s.",
                    serviceId,
                    expectedType.getName(),
                    guardType.getName()));

        return null;
    }
}
