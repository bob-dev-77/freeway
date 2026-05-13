package com.jujin.freeway.db.internal;

import com.jujin.freeway.db.RowMapper;
import com.jujin.freeway.db.RowMapperOverrides;

import java.util.Map;

public class RowMapperOverridesImpl implements RowMapperOverrides {

    private final Map<Class<?>, RowMapper<?>> mappers;

    public RowMapperOverridesImpl(Map<Class<?>, RowMapper<?>> mappers) {
        this.mappers = Map.copyOf(mappers);
    }

    @Override
    public RowMapper<?> get(Class<?> type) {
        return mappers.get(type);
    }
}
