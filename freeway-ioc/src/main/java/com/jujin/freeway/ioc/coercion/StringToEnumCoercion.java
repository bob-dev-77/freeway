package com.jujin.freeway.ioc.coercion;

import com.jujin.freeway.ioc.exception.UnknownValueException;

import java.util.Map;
import java.util.TreeMap;

/**
 * A {@link com.jujin.freeway.ioc.coercion.Coercion} for converting strings into
 * an instance of a particular enumerated type. The {@link Enum#name() name} is
 * used as the key to identify the enum instance, in a case-insensitive fashion.
 * <p>
 * Moved from freeway-core to freeway-ioc in release 5.3, but kept in same
 * package for compatibility. Moved freeway-ioc to commons in release 5.4, but
 * kept in same package for compatibility.
 *
 * @param <T>
 *            the type of enumeration
 */
public final class StringToEnumCoercion<T extends Enum<T>> implements Coercion<String, T> {

    private final Class<T> enumClass;

    private final Map<String, T> stringToEnum = new TreeMap<>(
        String.CASE_INSENSITIVE_ORDER);

    public StringToEnumCoercion(Class<T> enumClass) {
        this(enumClass, enumClass.getEnumConstants());
    }

    @SafeVarargs
    public StringToEnumCoercion(Class<T> enumClass, T... values) {
        this.enumClass = enumClass;

        for (T value : values)
            stringToEnum.put(value.name(), value);
    }

    @Override
    public T coerce(String input) {
        if (input == null || input.isBlank())
            return null;

        T result = stringToEnum.get(input);

        if (result == null) {
            String message = String.format(
                "Input '%s' does not identify a value from enumerated type %s.",
                input,
                enumClass.getName());

            throw new UnknownValueException(
                message,
                stringToEnum.keySet().stream().sorted().toList());
        }

        return result;
    }

    /**
     * Allows an alias value (alternate) string to reference a value.
     *
     */
    public StringToEnumCoercion<T> addAlias(String alias, T value) {
        stringToEnum.put(alias, value);

        return this;
    }

    public static <T extends Enum<T>> StringToEnumCoercion<T> create(
        Class<T> enumClass) {
        return new StringToEnumCoercion<T>(enumClass);
    }
}
