package com.jujin.freeway.ioc.coercion;

import com.jujin.freeway.ioc.config.MappedConfiguration;

/**
 * An immutable object that represents a mapping from one type to another. This
 * is also the contribution type when building the
 * {@link com.jujin.freeway.ioc.coercion.TypeCoercer} service. Wraps a
 * {@link com.jujin.freeway.ioc.coercion.Coercion} object that performs the work
 * with additional properties that describe the input and output types of the
 * coercion, needed when searching for an appropriate coercion (or sequence of
 * coercions).
 *
 * @param <S>
 *            source (input) type
 * @param <T>
 *            target (output) type
 */
public final class CoercionTuple<S, T> {

    private final Class<S> sourceType;

    private final Class<T> targetType;

    private final Coercion<S, T> coercion;

    private final Key key;

    /**
     * Wraps an arbitrary coercion with an implementation of toString() that
     * identifies the source and target types.
     */
    private class CoercionWrapper<WS, WT> implements Coercion<WS, WT> {

        private final Coercion<WS, WT> coercion;

        public CoercionWrapper(Coercion<WS, WT> coercion) {
            this.coercion = coercion;
        }

        @Override
        public WT coerce(WS input) {
            return coercion.coerce(input);
        }

        @Override
        public String toString() {
            return String.format(
                "%s --> %s",
                convert(sourceType),
                convert(targetType));
        }
    }

    private String convert(Class<?> type) {
        if (Void.class.equals(type))
            return "null";

        String name = type.getCanonicalName();

        int dotx = name.lastIndexOf('.');

        // Strip off a package name of "java.lang"

        if (dotx > 0 && name.substring(0, dotx).equals("java.lang"))
            return name.substring(dotx + 1);

        return name;
    }

    /**
     * Standard constructor, which defaults wrap to true.
     */
    public CoercionTuple(
        Class<S> sourceType,
        Class<T> targetType,
        Coercion<S, T> coercion) {
        this(sourceType, targetType, coercion, true);
    }

    /**
     * Convenience method to create a coercion tuple using
     * {@linkplain #create(Class, Class, Coercion)} and add it to a
     * {@linkplain MappedConfiguration} in a single step.
     *
     */
    @SuppressWarnings("rawtypes")
    public static <S, T> void add(
        MappedConfiguration<CoercionTuple.Key, CoercionTuple> configuration,
        Class<S> sourceType,
        Class<T> targetType,
        Coercion<S, T> coercion) {
        var coercionTuple = new CoercionTuple<S, T>(
            sourceType,
            targetType,
            coercion);
        configuration.add(coercionTuple.getKey(), coercionTuple);
    }

    /**
     * Convenience method to create a coercion tuple using
     * {@linkplain #create(Class, Class, Coercion)} and override a matching one in a
     * {@linkplain MappedConfiguration} in a single step.
     *
     */
    @SuppressWarnings("rawtypes")
    public static <S, T> void override(
        MappedConfiguration<CoercionTuple.Key, CoercionTuple> configuration,
        Class<S> sourceType,
        Class<T> targetType,
        Coercion<S, T> coercion) {
        var coercionTuple = new CoercionTuple<S, T>(
            sourceType,
            targetType,
            coercion);
        configuration.override(coercionTuple.getKey(), coercionTuple);
    }

    /**
     * Convenience constructor to help with generics.
     *
     */
    public static <S, T> CoercionTuple<S, T> create(
        Class<S> sourceType,
        Class<T> targetType,
        Coercion<S, T> coercion) {
        return new CoercionTuple<S, T>(sourceType, targetType, coercion);
    }

    /**
     * Internal-use constructor.
     *
     * @param sourceType
     *            the source (or input) type of the coercion, may be Void.class to
     *            indicate a coercion from null
     * @param targetType
     *            the target (or output) type of the coercion
     * @param coercion
     *            the object that performs the coercion
     * @param wrap
     *            if true, the coercion is wrapped to provide a useful toString()
     */
    @SuppressWarnings("unchecked")
    public CoercionTuple(
        Class<S> sourceType,
        Class<T> targetType,
        Coercion<S, T> coercion,
        boolean wrap) {
        assert sourceType != null;
        assert targetType != null;
        assert coercion != null;

        this.sourceType = (Class<S>) toWrapperType(sourceType);
        this.targetType = (Class<T>) toWrapperType(targetType);
        this.coercion = wrap ? new CoercionWrapper<S, T>(coercion) : coercion;
        this.key = new Key();
    }

    @Override
    public String toString() {
        return coercion.toString();
    }

    public Coercion<S, T> getCoercion() {
        return coercion;
    }

    public Class<S> getSourceType() {
        return sourceType;
    }

    public Class<T> getTargetType() {
        return targetType;
    }

    public Key getKey() {
        return key;
    }

    /**
     * Class that represents the key to be used to the mapped configuration of the
     * {@link TypeCoercer} service.
     */
    public final class Key {

        protected Class<S> getSourceType() {
            return sourceType;
        }

        protected Class<T> getTargetType() {
            return targetType;
        }

        @Override
        public String toString() {
            return String.format(
                "%s -> %s",
                sourceType.getName(),
                targetType.getName());
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result +
                ((sourceType == null) ? 0 : sourceType.hashCode());
            result = prime * result +
                ((targetType == null) ? 0 : targetType.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;

            @SuppressWarnings("unchecked")
            Key other = (Key) obj;
            if (sourceType == null) {
                if (other.getSourceType() != null)
                    return false;
            } else if (!sourceType.equals(other.getSourceType()))
                return false;
            if (targetType == null) {
                if (other.getTargetType() != null)
                    return false;
            } else if (!targetType.equals(other.getTargetType()))
                return false;
            return true;
        }
    }

    private static Class<?> toWrapperType(Class<?> clazz) {
        if (clazz == int.class)
            return Integer.class;
        if (clazz == long.class)
            return Long.class;
        if (clazz == double.class)
            return Double.class;
        if (clazz == float.class)
            return Float.class;
        if (clazz == boolean.class)
            return Boolean.class;
        if (clazz == char.class)
            return Character.class;
        if (clazz == short.class)
            return Short.class;
        if (clazz == byte.class)
            return Byte.class;
        return clazz;
    }
}
