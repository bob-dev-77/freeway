package com.jujin.freeway.ioc.internal.util;

import java.lang.reflect.Type;

/**
 * Chain of command for InjectionDefaultProvider.
 */
public class DelegatingInjectionResources implements InjectionResources {
    private final InjectionResources first;
    private final InjectionResources next;

    public DelegatingInjectionResources(InjectionResources first,
        InjectionResources next) {
        this.first = first;
        this.next = next;
    }

    @Override
    public <T> T findResource(Class<T> type, Type genericType) {
        T result = first.findResource(type, genericType);

        return result != null ? result : next.findResource(type, genericType);
    }
}
