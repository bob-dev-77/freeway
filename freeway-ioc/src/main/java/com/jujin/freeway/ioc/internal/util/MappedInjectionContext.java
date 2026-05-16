package com.jujin.freeway.ioc.internal.util;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * InjectionDefaultProvider that operates using a Map from type to value.
 */
public class MappedInjectionContext implements InjectionContext {

    private final Map<Class<?>, Object> map;

    public MappedInjectionContext(Map<Class<?>, Object> map) {
        this.map = map;
    }

    @Override
    @SuppressWarnings({ "unchecked" })
    public <T> T findResource(Class<T> type, Type genericType) {
        return (T) map.get(type);
    }
}
