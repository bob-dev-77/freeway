package com.jujin.freeway.commons.json;

import com.jujin.freeway.commons.json.exceptions.JSONException;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Static utility methods for JSON serialization and deserialization.
 *
 * <p>
 * Provides the entry points for converting arbitrary Java objects to/from JSON
 * strings, as well as low-level helpers used by {@link JSONObject},
 * {@link JSONArray} and the print sessions.
 */
public final class JSONUtils {

    private static final Double NEGATIVE_ZERO = -0d;

    private JSONUtils() {}

    // ============================================================
    // Public entry points
    // ============================================================

    /** Serialize any value to a compact JSON string. */
    public static String toJson(Object value) {
        var buf = new StringBuilder();
        writeValue(buf, value, false, 0);
        return buf.toString();
    }

    /** Serialize with pretty-print (2-space indent). */
    public static String toJsonPretty(Object value) {
        var buf = new StringBuilder();
        writeValue(buf, value, true, 0);
        return buf.toString();
    }

    /** Parse JSON string into a JSONObject, JSONArray, or primitive. */
    public static Object fromJson(String json) {
        Object result = new JSONTokener(json).nextValue(null);
        return result == JSONObject.NULL ? null : result;
    }

    /**
     * Parse JSON string and convert to the target type (POJO, Record, Map, etc.).
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(String json, Class<T> type) {
        Object parsed = fromJson(json);
        if (parsed == null)
            return null;
        return (T) convert(parsed, type);
    }

    /** Parse JSON string and convert to the target generic type. */
    public static Object fromJson(String json, java.lang.reflect.Type type) {
        Object parsed = fromJson(json);
        if (parsed == null)
            return null;
        return convertType(parsed, type);
    }

    /**
     * Encodes the number as a JSON string.
     */
    public static String numberToString(Number number) {
        if (number == null) {
            throw new RuntimeException("Number must be non-null");
        }

        double doubleValue = number.doubleValue();
        checkDouble(doubleValue);

        // the original returns "-0" instead of "-0.0" for negative zero
        if (number.equals(NEGATIVE_ZERO)) {
            return "-0";
        }

        long longValue = number.longValue();
        if (doubleValue == (double) longValue) {
            return Long.toString(longValue);
        }

        return number.toString();
    }

    static String doubleToString(double d) {
        if (Double.isInfinite(d) || Double.isNaN(d)) {
            return "null";
        }
        return numberToString(d);
    }

    /**
     * Escapes special characters in a string for JSON output (no surrounding
     * quotes).
     */
    static String escape(String value) {
        StringBuilder out = new StringBuilder();
        char prev = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '/' -> {
                    if (prev == '<')
                        out.append('\\');
                    out.append(c);
                }
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c <= 0x1F ||
                        (c >= 0x0080 && c < 0x00a0) ||
                        (c >= 0x2000 && c < 0x2100)) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
            prev = c;
        }
        return out.toString();
    }

    /**
     * Encodes {@code data} as a JSON string. This applies quotes and any necessary
     * character escaping.
     */
    public static String quote(String data) {
        if (data == null)
            return "\"\"";
        return "\"" + escape(data) + "\"";
    }

    // ============================================================
    // Internal serialization
    // ============================================================

    static void writeValue(
        StringBuilder buf,
        Object value,
        boolean pretty,
        int indent) {
        if (value == null || value == JSONObject.NULL) {
            buf.append("null");
        } else if (value instanceof String s) {
            buf.append(quote(s));
        } else if (value instanceof Boolean || value instanceof Number) {
            buf.append(value);
        } else if (value instanceof JSONObject obj) {
            buf.append(pretty ? obj.toString() : obj.toCompactString());
        } else if (value instanceof JSONArray arr) {
            buf.append(pretty ? arr.toString() : arr.toCompactString());
        } else if (value instanceof Map<?, ?> m) {
            writeMap(buf, m, pretty, indent);
        } else if (value instanceof Collection<?> c) {
            writeCollection(buf, c, pretty, indent);
        } else if (value.getClass().isArray()) {
            writeArray(buf, value, pretty, indent);
        } else if (value instanceof Enum<?> e) {
            buf.append(quote(e.name()));
        } else {
            writeBean(buf, value, pretty, indent);
        }
    }

    @SuppressWarnings("unchecked")
    private static void writeMap(
        StringBuilder buf,
        Map<?, ?> map,
        boolean pretty,
        int indent) {
        buf.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : ((Map<Object, Object>) map).entrySet()) {
            if (first)
                first = false;
            else
                buf.append(',');
            if (pretty) {
                buf.append('\n');
                indentBuf(buf, indent + 1);
            }
            buf.append(quote(Objects.toString(e.getKey())));
            buf.append(':');
            if (pretty)
                buf.append(' ');
            writeValue(buf, e.getValue(), pretty, indent + 1);
        }
        if (pretty && !first) {
            buf.append('\n');
            indentBuf(buf, indent);
        }
        buf.append('}');
    }

    private static void writeCollection(
        StringBuilder buf,
        Collection<?> coll,
        boolean pretty,
        int indent) {
        buf.append('[');
        boolean first = true;
        for (Object item : coll) {
            if (first)
                first = false;
            else
                buf.append(',');
            if (pretty) {
                buf.append('\n');
                indentBuf(buf, indent + 1);
            }
            writeValue(buf, item, pretty, indent + 1);
        }
        if (pretty && !first) {
            buf.append('\n');
            indentBuf(buf, indent);
        }
        buf.append(']');
    }

    private static void writeArray(
        StringBuilder buf,
        Object array,
        boolean pretty,
        int indent) {
        buf.append('[');
        int len = Array.getLength(array);
        for (int i = 0; i < len; i++) {
            if (i > 0)
                buf.append(',');
            if (pretty) {
                buf.append('\n');
                indentBuf(buf, indent + 1);
            }
            writeValue(buf, Array.get(array, i), pretty, indent + 1);
        }
        if (pretty && len > 0) {
            buf.append('\n');
            indentBuf(buf, indent);
        }
        buf.append(']');
    }

    private static void writeBean(
        StringBuilder buf,
        Object bean,
        boolean pretty,
        int indent) {
        buf.append('{');
        boolean first = true;
        for (var entry : beanProperties(bean)) {
            if (first)
                first = false;
            else
                buf.append(',');
            if (pretty) {
                buf.append('\n');
                indentBuf(buf, indent + 1);
            }
            buf.append(quote(entry.getKey()));
            buf.append(':');
            if (pretty)
                buf.append(' ');
            writeValue(buf, entry.getValue(), pretty, indent + 1);
        }
        if (pretty && !first) {
            buf.append('\n');
            indentBuf(buf, indent);
        }
        buf.append('}');
    }

    private static void indentBuf(StringBuilder buf, int level) {
        buf.append("  ".repeat(Math.max(0, level)));
    }

    // ============================================================
    // Bean property extraction (records + POJOs)
    // ============================================================

    private static List<Map.Entry<String, Object>> beanProperties(Object bean) {
        if (bean instanceof Record rec) {
            var components = rec.getClass().getRecordComponents();
            List<Map.Entry<String, Object>> props = new ArrayList<>(
                components.length);
            for (var comp : components) {
                try {
                    props.add(
                        Map.entry(
                            comp.getName(),
                            comp.getAccessor().invoke(rec)));
                } catch (ReflectiveOperationException e) {
                    props.add(Map.entry(comp.getName(), null));
                }
            }
            return props;
        }
        List<Map.Entry<String, Object>> props = new ArrayList<>();
        for (var m : bean.getClass().getMethods()) {
            String name = m.getName();
            if (m.getParameterCount() != 0 || m.getReturnType() == void.class)
                continue;
            if (name.startsWith("get") &&
                name.length() > 3 &&
                Character.isUpperCase(name.charAt(3))) {
                String prop = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                if (prop.equals("class"))
                    continue;
                try {
                    props.add(Map.entry(prop, m.invoke(bean)));
                } catch (ReflectiveOperationException e) {
                    props.add(Map.entry(prop, null));
                }
            } else if (name.startsWith("is") &&
                name.length() > 2 &&
                Character.isUpperCase(name.charAt(2)) &&
                (m.getReturnType() == boolean.class ||
                    m.getReturnType() == Boolean.class)) {
                String prop = Character.toLowerCase(name.charAt(2)) + name.substring(3);
                try {
                    props.add(Map.entry(prop, m.invoke(bean)));
                } catch (ReflectiveOperationException e) {
                    props.add(Map.entry(prop, null));
                }
            }
        }
        return props;
    }

    // ============================================================
    // Type conversion (parsed → typed POJO/Record)
    // ============================================================

    private static Object convertType(
        Object value,
        java.lang.reflect.Type target) {
        if (target instanceof Class<?> c)
            return convert(value, c);
        if (target instanceof java.lang.reflect.ParameterizedType pt) {
            Class<?> raw = (Class<?>) pt.getRawType();
            java.lang.reflect.Type[] args = pt.getActualTypeArguments();
            if (List.class.isAssignableFrom(raw) &&
                value instanceof List<?> list) {
                if (args.length == 1 && args[0] instanceof Class<?> elemType) {
                    List<Object> result = new ArrayList<>(list.size());
                    for (Object item : list)
                        result.add(convert(item, elemType));
                    return result;
                }
            }
            if (Map.class.isAssignableFrom(raw) &&
                value instanceof Map<?, ?> map) {
                if (args.length == 2 && args[1] instanceof Class<?> valType) {
                    Map<String, Object> result = new LinkedHashMap<>();
                    for (var e : map.entrySet()) {
                        result.put(
                            (String) e.getKey(),
                            convert(e.getValue(), valType));
                    }
                    return result;
                }
            }
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Object convert(Object value, Class<?> target) {
        if (value == null)
            return null;
        if (target.isInstance(value))
            return value;
        if (value instanceof Number n) {
            if (target == int.class || target == Integer.class)
                return n.intValue();
            if (target == long.class || target == Long.class)
                return n.longValue();
            if (target == double.class || target == Double.class)
                return n.doubleValue();
            if (target == float.class || target == Float.class)
                return n.floatValue();
            if (target == short.class || target == Short.class)
                return n.shortValue();
            if (target == byte.class || target == Byte.class)
                return n.byteValue();
            return value;
        }
        if (value instanceof String s) {
            if (target == char.class || target == Character.class) {
                if (s.length() != 1)
                    throw new JSONException(
                        "Cannot convert string to char: " + s);
                return s.charAt(0);
            }
            if (target.isEnum()) {
                for (var c : target.getEnumConstants()) {
                    if (((Enum<?>) c).name().equals(s))
                        return c;
                }
                throw new JSONException(
                    "No enum constant " + target.getName() + "." + s);
            }
            return value;
        }
        if (value instanceof Boolean &&
            (target == boolean.class || target == Boolean.class)) {
            return value;
        }
        if (value instanceof JSONObject obj) {
            if (Map.class.isAssignableFrom(target))
                return obj;
            return convertToBean(obj.toMap(), target);
        }
        if (value instanceof JSONArray arr) {
            if (List.class.isAssignableFrom(target))
                return arr.toList();
            if (target.isArray()) {
                List<Object> list = arr.toList();
                var componentType = target.getComponentType();
                Object result = Array.newInstance(componentType, list.size());
                for (int i = 0; i < list.size(); i++) {
                    Array.set(result, i, convert(list.get(i), componentType));
                }
                return result;
            }
            return arr;
        }
        if (value instanceof Map<?, ?> map) {
            if (target == JSONObject.class) {
                JSONObject obj = new JSONObject();
                for (var e : map.entrySet())
                    obj.put((String) e.getKey(), e.getValue());
                return obj;
            }
            if (Map.class.isAssignableFrom(target))
                return value;
            return convertToBean((Map<String, Object>) map, target);
        }
        if (value instanceof List<?> list) {
            if (target == JSONArray.class) {
                JSONArray arr = new JSONArray();
                for (var item : list)
                    arr.add(item);
                return arr;
            }
            if (List.class.isAssignableFrom(target))
                return value;
            return value;
        }
        return value;
    }

    private static Object convertToBean(
        Map<String, Object> data,
        Class<?> type) {
        if (type.isRecord()) {
            var components = type.getRecordComponents();
            Object[] args = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                var comp = components[i];
                Object val = data.get(comp.getName());
                args[i] = val != null ? convert(val, comp.getType()) : null;
            }
            try {
                return type.getConstructors()[0].newInstance(args);
            } catch (ReflectiveOperationException e) {
                throw new JSONException(
                    "Cannot construct record " + type.getName(),
                    e);
            }
        }
        try {
            Object bean = type.getConstructor().newInstance();
            for (var entry : data.entrySet()) {
                String prop = entry.getKey();
                Object val = entry.getValue();
                String setterName = "set" +
                    Character.toUpperCase(prop.charAt(0)) +
                    prop.substring(1);
                boolean set = false;
                for (var m : type.getMethods()) {
                    if (m.getName().equals(setterName) &&
                        m.getParameterCount() == 1) {
                        m.invoke(bean, convert(val, m.getParameterTypes()[0]));
                        set = true;
                        break;
                    }
                }
                if (!set) {
                    try {
                        var field = findField(type, prop);
                        if (field != null) {
                            field.setAccessible(true);
                            field.set(bean, convert(val, field.getType()));
                        }
                    } catch (NoSuchFieldException ignored) {}
                }
            }
            return bean;
        } catch (NoSuchMethodException e) {
            throw new JSONException(
                "Type " + type.getName() + " has no no-arg constructor",
                e);
        } catch (ReflectiveOperationException e) {
            throw new JSONException("Cannot construct " + type.getName(), e);
        }
    }

    private static Field findField(Class<?> type, String name)
        throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    // ============================================================
    // NaN / Infinity check
    // ============================================================

    static void checkDouble(double d) {
        if (Double.isInfinite(d) || Double.isNaN(d)) {
            throw new RuntimeException(
                "JSON does not allow non-finite numbers.");
        }
    }

    // ============================================================
    // Print session support (called by JSONArray.print())
    // ============================================================

    static void printValue(JSONPrintSession session, Object value) {
        if (value == null || value == JSONObject.NULL) {
            session.print("null");
            return;
        }
        if (value instanceof JSONObject) {
            ((JSONObject) value).print(session);
            return;
        }

        if (value instanceof JSONArray) {
            ((JSONArray) value).print(session);
            return;
        }

        if (value instanceof JSONString) {
            String printValue = ((JSONString) value).toJSONString();
            session.print(printValue);
            return;
        }

        if (value instanceof Number) {
            String printValue = numberToString((Number) value);
            session.print(printValue);
            return;
        }

        if (value instanceof Boolean) {
            session.print(value.toString());
            return;
        }

        // Otherwise it really should just be a string. Nothing else can go in.
        session.printQuoted(value.toString());
    }
}
