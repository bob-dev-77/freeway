package com.jujin.freeway.commons.logging;

import org.slf4j.ILoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

/**
 * Factory that creates {@link JULLoggerAdapter} instances wrapping
 * {@link java.util.logging.Logger}.
 */
public class JULLoggerFactory implements ILoggerFactory {

    private final ConcurrentMap<String, JULLoggerAdapter> loggerMap = new ConcurrentHashMap<>();

    @Override
    public org.slf4j.Logger getLogger(String name) {
        JULLoggerAdapter adapter = loggerMap.get(name);
        if (adapter != null) {
            return adapter;
        }

        Logger julLogger = Logger.getLogger(name);
        var newAdapter = new JULLoggerAdapter(julLogger);
        JULLoggerAdapter existing = loggerMap.putIfAbsent(name, newAdapter);
        return existing != null ? existing : newAdapter;
    }
}
