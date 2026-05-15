package com.jujin.freeway.db.internal;

import com.jujin.freeway.db.RowMapper;
import com.jujin.freeway.db.RowMapperOverrides;
import com.jujin.freeway.db.SqlException;
import com.jujin.freeway.ioc.coercion.TypeCoercer;
import com.jujin.freeway.ioc.property.PropertyAccess;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory that creates {@link RowMapper} instances for different types.
 * <p>
 * Supports three mapping strategies:
 * <ul>
 *   <li><b>Simple types</b> — String, primitives, wrappers, BigDecimal, date/time types</li>
 *   <li><b>Records</b> — uses canonical constructor with column-to-component matching</li>
 *   <li><b>Beans</b> — uses no-arg constructor + property setters (IoC MethodHandle or reflection)</li>
 * </ul>
 * <p>
 * Type coercion delegates entirely to IoC's {@link TypeCoercer} when available,
 * ensuring consistent conversion behavior across the framework.
 */
@SuppressWarnings("unchecked")
public class RowMapperFactory {

    private final TypeCoercer typeCoercer;
    private final PropertyAccess propertyAccess;
    private final RowMapperOverrides overrides;
    private final Map<Class<?>, RowMapper<?>> cache = new ConcurrentHashMap<>();

    /**
     * Creates a standalone factory without IoC services.
     * <p>
     * <b>Note:</b> Without TypeCoercer, only identity conversion is supported.
     * Use {@link #withIoC(TypeCoercer, PropertyAccess, RowMapperOverrides)} for full functionality.
     */
    public static RowMapperFactory standalone() {
        return new RowMapperFactory(null, null, null);
    }

    /**
     * Creates an IoC-enhanced factory with full type coercion and optimized property access.
     *
     * @param typeCoercer   IoC type conversion service
     * @param propertyAccess IoC property access service (MethodHandle-based)
     * @return configured factory instance
     */
    /**
     * Creates an IoC-enhanced factory with full type coercion, optimized property access,
     * and custom mapper overrides.
     *
     * @param typeCoercer   IoC type conversion service
     * @param propertyAccess IoC property access service (MethodHandle-based)
     * @param overrides     Custom RowMapper overrides (can be null)
     * @return configured factory instance
     */
    public static RowMapperFactory withIoC(TypeCoercer typeCoercer, PropertyAccess propertyAccess,
                                          RowMapperOverrides overrides) {
        return new RowMapperFactory(typeCoercer, propertyAccess, overrides);
    }

    private RowMapperFactory(TypeCoercer typeCoercer, PropertyAccess propertyAccess) {
        this(typeCoercer, propertyAccess, null);
    }

    private RowMapperFactory(TypeCoercer typeCoercer, PropertyAccess propertyAccess,
                            RowMapperOverrides overrides) {
        this.typeCoercer = typeCoercer;
        this.propertyAccess = propertyAccess;
        this.overrides = overrides;
    }

    /**
     * Returns a mapper for {@code type}. Custom overrides take precedence;
     * built-in mappers are cached for performance.
     *
     * @param type the target type to map rows into
     * @return a RowMapper instance
     */
    public <T> RowMapper<T> forClass(Class<T> type) {
        // Check custom overrides first (from IoC contribution)
        if (overrides != null) {
            RowMapper<?> custom = overrides.get(type);
            if (custom != null) return (RowMapper<T>) custom;
        }
        
        return (RowMapper<T>) cache.computeIfAbsent(type, this::create);
    }

    // ── Mapper dispatch ──────────────────────────────────────────────

    private <T> RowMapper<T> create(Class<T> type) {
        if (isSimpleType(type)) return createSimpleMapper(type);
        if (type.isRecord()) return createRecordMapper(type);
        return createBeanMapper(type);
    }

    // ── Simple types ─────────────────────────────────────────────────

    private boolean isSimpleType(Class<?> type) {
        return type == String.class
            || type == Integer.class || type == int.class
            || type == Long.class || type == long.class
            || type == Double.class || type == double.class
            || type == Float.class || type == float.class
            || type == Short.class || type == short.class
            || type == Byte.class || type == byte.class
            || type == Boolean.class || type == boolean.class
            || type == BigDecimal.class
            || type == LocalDate.class
            || type == LocalDateTime.class
            || type == Instant.class
            || type == LocalTime.class
            || type == byte[].class;
    }

    private <T> RowMapper<T> createSimpleMapper(Class<T> type) {
        return (rs, rowNum) -> coerce(rs.getObject(1), type);
    }

    // ── Record mapper ────────────────────────────────────────────────

    private <T> RowMapper<T> createRecordMapper(Class<T> type) {
        RecordComponent[] components = type.getRecordComponents();
        Constructor<T> ctor = canonicalConstructor(type, components);
        int[] columnIndexes = new int[components.length];
        for (int i = 0; i < components.length; i++) columnIndexes[i] = -1;

        return (rs, rowNum) -> {
            if (rowNum == 0) {
                var meta = rs.getMetaData();
                for (int ci = 0; ci < components.length; ci++) {
                    columnIndexes[ci] = findColumn(meta, components[ci].getName());
                }
            }
            Object[] args = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                int col = columnIndexes[i];
                args[i] = col >= 1
                    ? coerce(rs.getObject(col), components[i].getType())
                    : nullValue(components[i].getType());
            }
            try {
                return ctor.newInstance(args);
            } catch (Exception e) {
                throw new SqlException("Failed to construct " + type.getName(), e);
            }
        };
    }

    // ── Bean mapper (unified via strategy pattern) ───────────────────

    private <T> RowMapper<T> createBeanMapper(Class<T> type) {
        PropertySetter setter = propertyAccess != null
            ? new IocPropertySetter(propertyAccess.getAdapter(type))
            : new ReflectionPropertySetter(type);

        return createGenericBeanMapper(type, setter);
    }

    /** Generic bean mapper template — works with any PropertySetter strategy. */
    private <T> RowMapper<T> createGenericBeanMapper(Class<T> type, PropertySetter setter) {
        String[] writableProps = setter.getPropertyNames();
        int[] columnIndexes = new int[writableProps.length];

        return (rs, rowNum) -> {
            if (rowNum == 0) {
                var meta = rs.getMetaData();
                for (int i = 0; i < writableProps.length; i++) {
                    columnIndexes[i] = findColumn(meta, writableProps[i]);
                }
            }
            T instance;
            try {
                instance = type.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new SqlException("Failed to instantiate " + type.getName(), e);
            }
            for (int i = 0; i < writableProps.length; i++) {
                int col = columnIndexes[i];
                if (col < 1) continue;
                Object value = coerce(rs.getObject(col), setter.getPropertyType(writableProps[i]));
                setter.set(instance, writableProps[i], value);
            }
            return instance;
        };
    }

    /** Strategy interface for setting bean properties. */
    private interface PropertySetter {
        String[] getPropertyNames();
        Class<?> getPropertyType(String propertyName);
        void set(Object instance, String propertyName, Object value);
    }

    /** IoC-based property setter using MethodHandle (high performance). */
    private static class IocPropertySetter implements PropertySetter {
        private final com.jujin.freeway.ioc.property.BeanPropertyAdapter adapter;
        private final String[] writableProps;

        IocPropertySetter(com.jujin.freeway.ioc.property.BeanPropertyAdapter adapter) {
            this.adapter = adapter;
            this.writableProps = adapter.getPropertyNames().stream()
                .filter(n -> adapter.getPropertyAdapter(n).isUpdate())
                .toArray(String[]::new);
        }

        @Override
        public String[] getPropertyNames() {
            return writableProps;
        }

        @Override
        public Class<?> getPropertyType(String propertyName) {
            return adapter.getPropertyAdapter(propertyName).getType();
        }

        @Override
        public void set(Object instance, String propertyName, Object value) {
            adapter.set(instance, propertyName, value);
        }
    }

    /** Reflection-based property setter using java.beans.Introspector (fallback). */
    private static class ReflectionPropertySetter implements PropertySetter {
        private final PropertyDescriptor[] writableProps;

        ReflectionPropertySetter(Class<?> type) {
            try {
                var allProps = Introspector.getBeanInfo(type).getPropertyDescriptors();
                this.writableProps = java.util.Arrays.stream(allProps)
                    .filter(pd -> pd.getWriteMethod() != null)
                    .toArray(PropertyDescriptor[]::new);
            } catch (Exception e) {
                throw new SqlException("Failed to introspect " + type.getName(), e);
            }
        }

        @Override
        public String[] getPropertyNames() {
            String[] names = new String[writableProps.length];
            for (int i = 0; i < writableProps.length; i++) {
                names[i] = writableProps[i].getName();
            }
            return names;
        }

        @Override
        public Class<?> getPropertyType(String propertyName) {
            for (PropertyDescriptor pd : writableProps) {
                if (pd.getName().equals(propertyName)) {
                    return pd.getPropertyType();
                }
            }
            throw new IllegalArgumentException("Property not found: " + propertyName);
        }

        @Override
        public void set(Object instance, String propertyName, Object value) {
            for (PropertyDescriptor pd : writableProps) {
                if (pd.getName().equals(propertyName)) {
                    try {
                        pd.getWriteMethod().invoke(instance, value);
                        return;
                    } catch (Exception e) {
                        throw new SqlException(
                            "Failed to set " + propertyName + " on " + instance.getClass().getName(), e);
                    }
                }
            }
            throw new IllegalArgumentException("Property not found: " + propertyName);
        }
    }

    // ── Column matching ──────────────────────────────────────────────

    static int findColumn(java.sql.ResultSetMetaData meta, String propertyName)
        throws SQLException {
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String col = meta.getColumnLabel(i);
            if (col == null) col = meta.getColumnName(i);
            if (propertyName.equalsIgnoreCase(col)) return i;
        }
        String snake = camelToSnake(propertyName);
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String col = meta.getColumnLabel(i);
            if (col == null) col = meta.getColumnName(i);
            if (snake.equalsIgnoreCase(col)) return i;
        }
        return -1;
    }

    private static final Map<String, String> SNAKE_CACHE = new ConcurrentHashMap<>();

    static String camelToSnake(String camel) {
        return SNAKE_CACHE.computeIfAbsent(camel, k -> {
            var sb = new StringBuilder();
            for (int i = 0; i < camel.length(); i++) {
                char c = camel.charAt(i);
                if (Character.isUpperCase(c)) {
                    char prev = i > 0 ? camel.charAt(i - 1) : '\0';
                    char next = i + 1 < camel.length() ? camel.charAt(i + 1) : '\0';
                    if ((prev != '\0' && Character.isLowerCase(prev))
                        || (next != '\0' && Character.isLowerCase(next) && !Character.isLowerCase(prev))) {
                        sb.append('_');
                    }
                    sb.append(Character.toLowerCase(c));
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        });
    }

    // ── Type coercion (delegates to IoC TypeCoercer) ─────────────────

    /**
     * Coerces a value to the target type using IoC's TypeCoercer.
     * <p>
     * Falls back to basic primitive wrapper conversion if TypeCoercer is not available.
     */
    <T> T coerce(Object value, Class<T> target) {
        if (value == null) return nullValue(target);
        if (target.isInstance(value)) return (T) value;

        if (typeCoercer != null) {
            try {
                return typeCoercer.coerce(value, target);
            } catch (Exception e) {
                throw new SqlException(
                    "Failed to coerce " + value.getClass().getSimpleName() +
                    " to " + target.getSimpleName() + ": " + e.getMessage(), e);
            }
        }

        // Standalone mode: support basic primitive wrapper conversions
        return basicPrimitiveCoerce(value, target);
    }

    /** Minimal coercion for primitive wrappers (Long→long, Integer→int, etc.). */
    @SuppressWarnings("unchecked")
    private static <T> T basicPrimitiveCoerce(Object value, Class<T> target) {
        if (value instanceof Number n) {
            if (target == int.class || target == Integer.class) return (T) (Integer) n.intValue();
            if (target == long.class || target == Long.class) return (T) (Long) n.longValue();
            if (target == double.class || target == Double.class) return (T) (Double) n.doubleValue();
            if (target == float.class || target == Float.class) return (T) (Float) n.floatValue();
            if (target == short.class || target == Short.class) return (T) (Short) n.shortValue();
            if (target == byte.class || target == Byte.class) return (T) (Byte) n.byteValue();
            if (target == boolean.class || target == Boolean.class)
                return (T) (Boolean) (n.intValue() != 0);
        }
        
        throw new SqlException(
            "TypeCoercer not available. Cannot convert " +
            value.getClass().getSimpleName() + " to " + target.getSimpleName() +
            ". Use RowMapperFactory.withIoC() for full type conversion support."
        );
    }

    private static <T> T nullValue(Class<T> target) {
        if (target == int.class) return (T) (Integer) 0;
        if (target == long.class) return (T) (Long) 0L;
        if (target == double.class) return (T) (Double) 0.0;
        if (target == float.class) return (T) (Float) 0.0f;
        if (target == short.class) return (T) (Short) (short) 0;
        if (target == byte.class) return (T) (Byte) (byte) 0;
        if (target == boolean.class) return (T) Boolean.FALSE;
        if (target == char.class) return (T) Character.valueOf('\0');
        return null;
    }

    // ── Constructor helper ───────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static <T> Constructor<T> canonicalConstructor(Class<T> type,
                                                           RecordComponent[] components) {
        Class<?>[] paramTypes = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++)
            paramTypes[i] = components[i].getType();
        try {
            Constructor<T> ctor = type.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            return ctor;
        } catch (NoSuchMethodException e) {
            throw new SqlException("Cannot find canonical constructor for " + type.getName(), e);
        }
    }
}
