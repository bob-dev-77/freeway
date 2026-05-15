package com.jujin.freeway.ioc.coercion;

import com.jujin.freeway.ioc.config.MappedConfiguration;
import java.util.Objects;

/**
 * An immutable record that represents a mapping from one type to another.
 * This is the contribution type when building the {@link TypeCoercer} service.
 * 
 * <p>Wraps a {@link Coercion} object with metadata describing the input and
 * output types, needed when searching for an appropriate coercion sequence.</p>
 *
 * @param sourceType   source (input) type
 * @param targetType   target (output) type
 * @param coercion     the coercion function
 * @param key          cache key for this tuple
 */
public record CoercionTuple<S, T>(
    Class<S> sourceType,
    Class<T> targetType,
    Coercion<S, T> coercion,
    Key key
) {
    
    /**
     * Standard constructor with auto-generated key.
     */
    public CoercionTuple(Class<S> sourceType, Class<T> targetType, Coercion<S, T> coercion) {
        this(
            Objects.requireNonNull(sourceType, "sourceType"),
            Objects.requireNonNull(targetType, "targetType"),
            Objects.requireNonNull(coercion, "coercion"),
            new Key(Objects.requireNonNull(sourceType), Objects.requireNonNull(targetType))
        );
    }
    
    /**
     * Convenience method to create and add a coercion tuple in one step.
     */
    @SuppressWarnings("rawtypes")
    public static <S, T> void add(
        MappedConfiguration<Key, CoercionTuple> configuration,
        Class<S> sourceType,
        Class<T> targetType,
        Coercion<S, T> coercion) {
        var tuple = new CoercionTuple<>(sourceType, targetType, coercion);
        configuration.add(tuple.key(), tuple);
    }
    
    /**
     * Convenience method to create and override a coercion tuple in one step.
     */
    @SuppressWarnings("rawtypes")
    public static <S, T> void override(
        MappedConfiguration<Key, CoercionTuple> configuration,
        Class<S> sourceType,
        Class<T> targetType,
        Coercion<S, T> coercion) {
        var tuple = new CoercionTuple<>(sourceType, targetType, coercion);
        configuration.override(tuple.key(), tuple);
    }
    
    /**
     * Factory method for better generics inference.
     */
    public static <S, T> CoercionTuple<S, T> create(
        Class<S> sourceType,
        Class<T> targetType,
        Coercion<S, T> coercion) {
        return new CoercionTuple<>(sourceType, targetType, coercion);
    }
    
    @Override
    public String toString() {
        return String.format("%s --> %s", formatType(sourceType), formatType(targetType));
    }
    
    private static String formatType(Class<?> type) {
        if (Void.class.equals(type)) {
            return "null";
        }
        
        String name = type.getCanonicalName();
        int dotIndex = name.lastIndexOf('.');
        
        // Strip java.lang package prefix
        if (dotIndex > 0 && name.substring(0, dotIndex).equals("java.lang")) {
            return name.substring(dotIndex + 1);
        }
        
        return name;
    }
    
    /**
     * Cache key for coercion lookup.
     * 
     * @param sourceType source type
     * @param targetType target type
     */
    public record Key(Class<?> sourceType, Class<?> targetType) {
        public Key {
            Objects.requireNonNull(sourceType);
            Objects.requireNonNull(targetType);
        }
        
        @Override
        public String toString() {
            return String.format("%s -> %s", sourceType.getName(), targetType.getName());
        }
    }
}
