package com.jujin.freeway.commons.json;

import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Implementations of {@link java.util.stream.Collector} that implement
 * reductions to {@code JSONArray} and to {@code JSONObject}.
 *
 */

public final class JSONCollectors {
    private JSONCollectors() {}

    /**
     * Returns a {@code Collector} that accumulates the input elements into a new
     * {@code JSONArray}.
     *
     * @param <T>
     *            the type of the input elements
     * @return a {@code Collector} which collects all the input elements into a
     *         {@code JSONArray}, in encounter order
     */
    public static <T> Collector<T, ?, JSONArray> toArray() {
        return Collector.of(JSONArray::new,
            JSONArray::add,
            JSONArray::putAll);
    }

    /**
     * Returns a {@code Collector} that accumulates elements into a
     * {@code JSONObject} whose keys and values are the result of applying the
     * provided mapping functions to the input elements.
     * <p>
     * In case of duplicate keys an {@code IllegalStateException} is thrown when the
     * collection operation is performed.
     *
     * @param <T>
     *            the type of the input elements
     * @param keyMapper
     *            a mapping function to produce String keys
     * @param valueMapper
     *            a mapping function to produce values
     * @return a {@code Collector} which collects elements into a {@code JSONObject}
     *         whose keys and values are the result of applying mapping functions to
     *         the input elements
     */
    public static <T> Collector<T, ?, JSONObject> toMap(Function<? super T, String> keyMapper,
        Function<? super T, Object> valueMapper) {
        return Collectors.toMap(keyMapper,
            valueMapper,
            (u, v) -> {
                throw new IllegalStateException(String.format("Duplicate key %s", u));
            },
            JSONObject::new);
    }

}
