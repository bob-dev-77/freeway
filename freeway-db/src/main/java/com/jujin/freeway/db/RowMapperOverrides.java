package com.jujin.freeway.db;

import com.jujin.freeway.ioc.annotations.UsesMappedConfiguration;

/**
 * A contribution point for custom {@link RowMapper} instances keyed by target
 * type. When a custom mapper is registered for a type, it takes precedence
 * over the built-in record/bean mapping.
 *
 * <pre>{@code
 * // In your module:
 * @Contribute(RowMapperOverrides.class)
 * public static void customMappers(MappedConfiguration<Class<?>, RowMapper<?>> config) {
 *     config.add(MyJsonType.class, (rs, rowNum) -> {
 *         return MyJsonType.parse(rs.getString("payload"));
 *     });
 * }
 * }</pre>
 */
@UsesMappedConfiguration(key = Class.class, value = RowMapper.class)
public interface RowMapperOverrides {

    /** Returns the custom mapper registered for {@code type}, or {@code null}. */
    RowMapper<?> get(Class<?> type);
}
