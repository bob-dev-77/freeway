package com.jujin.freeway.ioc.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.jujin.freeway.ioc.InvalidationEventHub;
import com.jujin.freeway.ioc.StringInterner;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.annotations.PostInjection;

public class StringInternerImpl implements StringInterner {
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @PostInjection
    public void setupInvalidation(InvalidationEventHub hub) {
        hub.clearOnInvalidation(cache);
    }

    public String intern(String string) {
        String result = cache.get(string);

        // Not yet in the cache? Add it.

        if (result == null) {
            cache.put(string, string);
            result = string;
        }

        return result;
    }

    public String format(String format, Object... arguments) {
        return intern(String.format(format, arguments));
    }
}
