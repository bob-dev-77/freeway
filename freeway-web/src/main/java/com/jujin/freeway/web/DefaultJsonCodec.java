package com.jujin.freeway.web;

import com.jujin.freeway.commons.json.JSONUtils;
import java.lang.reflect.Type;

/**
 * Default {@link JsonCodec} implementation backed by Freeway's built-in JSON
 * library.
 */
public class DefaultJsonCodec implements JsonCodec {

    @Override
    public String toJson(Object value) {
        return JSONUtils.toJson(value);
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        return JSONUtils.fromJson(json, type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T fromJson(String json, Type type) {
        return (T) JSONUtils.fromJson(json, type);
    }
}
