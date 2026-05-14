package com.jujin.freeway.db.internal;

import com.jujin.freeway.db.RowMapper;
import com.jujin.freeway.db.RowMapperOverrides;
import com.jujin.freeway.db.SqlException;
import com.jujin.freeway.ioc.property.PropertyAccess;
import com.jujin.freeway.ioc.coercion.TypeCoercer;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates {@link RowMapper} instances for records and JavaBeans. Records are
 * constructed via their canonical constructor; beans are populated via
 * {@link PropertyAccess} (when available) or {@code java.beans.Introspector}.
 *
 * <p>
 * Type coercion delegates to the IoC {@link TypeCoercer} when available,
 * falling back to built-in JDBC-to-Java conversions.
 *
 * <p>
 * Column names are matched to property/component names with automatic
 * snake_case-to-camelCase conversion (e.g. {@code created_at} matches
 * {@code createdAt}).
 */
@SuppressWarnings("unchecked")
public class DefaultRowMapper {

    private final TypeCoercer typeCoercer;
    private final PropertyAccess propertyAccess;
    private final RowMapperOverrides overrides;
    private final Map<Class<?>, RowMapper<?>> cache = new ConcurrentHashMap<>();

    /** Standalone: basic type coercion, no IoC services, no overrides. */
    public DefaultRowMapper() {
        this(null, null, null);
    }

    /** IoC-enhanced: uses TypeCoercer and PropertyAccess from the container. */
    public DefaultRowMapper(TypeCoercer typeCoercer, PropertyAccess propertyAccess) {
        this(typeCoercer, propertyAccess, null);
    }

    /** Full: IoC services plus custom RowMapper overrides. */
    public DefaultRowMapper(TypeCoercer typeCoercer, PropertyAccess propertyAccess,
                            RowMapperOverrides overrides) {
        this.typeCoercer = typeCoercer;
        this.propertyAccess = propertyAccess;
        this.overrides = overrides;
    }

    /**
     * Returns a mapper for {@code type}. Custom overrides contributed via
     * {@link RowMapperOverrides} take precedence; otherwise a built-in
     * record/bean/simple mapper is created and cached.
     */
    @SuppressWarnings("unchecked")
    public <T> RowMapper<T> forClass(Class<T> type) {
        // Check custom overrides first — these are not cached because they
        // come from user contributions and may change between reloads
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

    // ── Bean mapper ──────────────────────────────────────────────────

    private <T> RowMapper<T> createBeanMapper(Class<T> type) {
        if (propertyAccess != null) {
            return createIocBeanMapper(type);
        }
        return createReflectionBeanMapper(type);
    }

    /** Bean mapper using IoC PropertyAccess (MethodHandle-based setters). */
    private <T> RowMapper<T> createIocBeanMapper(Class<T> type) {
        var adapter = propertyAccess.getAdapter(type);
        var writableProps = adapter.getPropertyNames().stream()
            .filter(n -> adapter.getPropertyAdapter(n).isUpdate())
            .toArray(String[]::new);
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
                var prop = adapter.getPropertyAdapter(writableProps[i]);
                Object value = coerce(rs.getObject(col), prop.getType());
                adapter.set(instance, writableProps[i], value);
            }
            return instance;
        };
    }

    /** Fallback bean mapper using java.beans.Introspector directly. */
    private <T> RowMapper<T> createReflectionBeanMapper(Class<T> type) {
        PropertyDescriptor[] allProps;
        try {
            allProps = Introspector.getBeanInfo(type).getPropertyDescriptors();
        } catch (Exception e) {
            throw new SqlException("Failed to introspect " + type.getName(), e);
        }
        var writableProps = new ArrayList<PropertyDescriptor>();
        for (var pd : allProps) {
            if (pd.getWriteMethod() != null) writableProps.add(pd);
        }
        int[] columnIndexes = new int[writableProps.size()];

        return (rs, rowNum) -> {
            if (rowNum == 0) {
                var meta = rs.getMetaData();
                for (int i = 0; i < writableProps.size(); i++) {
                    columnIndexes[i] = findColumn(meta, writableProps.get(i).getName());
                }
            }
            T instance;
            try {
                instance = type.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new SqlException("Failed to instantiate " + type.getName(), e);
            }
            for (int i = 0; i < writableProps.size(); i++) {
                int col = columnIndexes[i];
                if (col < 1) continue;
                var pd = writableProps.get(i);
                Object value = coerce(rs.getObject(col), pd.getPropertyType());
                try {
                    pd.getWriteMethod().invoke(instance, value);
                } catch (Exception e) {
                    throw new SqlException(
                        "Failed to set " + pd.getName() + " on " + type.getName(), e);
                }
            }
            return instance;
        };
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
                    // Only insert underscore when this is the start of a new word:
                    // prev is lowercase → start of new word (e.g. "userId" → "user_id")
                    // OR next is lowercase and prev is uppercase → end of an abbreviation (e.g. "HTMLParser" → "html_parser")
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

    // ── Type coercion ────────────────────────────────────────────────

    /** Delegates to TypeCoercer when available, else uses built-in logic. */
    @SuppressWarnings("unchecked")
    <T> T coerce(Object value, Class<T> target) {
        if (value == null) return nullValue(target);
        if (target.isInstance(value)) return (T) value;

        if (typeCoercer != null) {
            try {
                return typeCoercer.coerce(value, target);
            } catch (Exception e) {
                // Fall through to basic coercion on failure
            }
        }

        return basicCoerce(value, target);
    }

    private static <T> T basicCoerce(Object value, Class<T> target) {
        if (value instanceof Number n) {
            if (target == int.class || target == Integer.class) return (T) (Integer) n.intValue();
            if (target == long.class || target == Long.class) return (T) (Long) n.longValue();
            if (target == double.class || target == Double.class) return (T) (Double) n.doubleValue();
            if (target == float.class || target == Float.class) return (T) (Float) n.floatValue();
            if (target == short.class || target == Short.class) return (T) (Short) n.shortValue();
            if (target == byte.class || target == Byte.class) return (T) (Byte) n.byteValue();
            if (target == BigDecimal.class) return (T) new BigDecimal(n.toString());
        }
        if (value instanceof String s) {
            if (target == int.class || target == Integer.class) return (T) Integer.valueOf(s);
            if (target == long.class || target == Long.class) return (T) Long.valueOf(s);
            if (target == double.class || target == Double.class) return (T) Double.valueOf(s);
            if (target == boolean.class || target == Boolean.class)
                return (T) Boolean.valueOf(!s.isBlank() && Boolean.parseBoolean(s));
            if (target == BigDecimal.class) return (T) new BigDecimal(s);
        }
        if (value instanceof Timestamp ts) {
            if (target == Instant.class) return (T) ts.toInstant();
            if (target == LocalDateTime.class) return (T) ts.toLocalDateTime();
        }
        if (value instanceof java.sql.Date sd && target == LocalDate.class)
            return (T) sd.toLocalDate();
        if (value instanceof java.sql.Time st && target == LocalTime.class)
            return (T) st.toLocalTime();
        if (value instanceof Number n && (target == boolean.class || target == Boolean.class))
            return (T) (Boolean) (n.intValue() != 0);

        try { return (T) value.toString(); } catch (Exception ignored) {}
        return (T) value;
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
