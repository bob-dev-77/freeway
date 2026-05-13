package com.jujin.freeway.web;

import java.lang.reflect.Type;

/**
 * Pluggable JSON serialization/deserialization codec. Replace the default
 * implementation by binding a different service.
 *
 * @see HttpContext
 */
public interface JsonCodec {

    /** Serialize any value to a JSON string. */
    String toJson(Object value);

    /** Parse JSON string and convert to the target type. */
    <T> T fromJson(String json, Class<T> type);

    /** Parse JSON string and convert to the target type (supports generics). */
    <T> T fromJson(String json, Type type);
}
