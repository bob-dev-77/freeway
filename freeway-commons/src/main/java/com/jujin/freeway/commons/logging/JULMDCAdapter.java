package com.jujin.freeway.commons.logging;

import org.slf4j.spi.MDCAdapter;

import java.util.*;

/**
 * A ThreadLocal-based MDC adapter for JDK logging.
 */
public class JULMDCAdapter implements MDCAdapter {

    private final ThreadLocal<Map<String, String>> context = ThreadLocal.withInitial(HashMap::new);
    private final ThreadLocal<Map<String, Deque<String>>> dequeMap = ThreadLocal.withInitial(HashMap::new);

    @Override
    public void put(String key, String val) {
        context.get().put(key, val);
    }

    @Override
    public String get(String key) {
        return context.get().get(key);
    }

    @Override
    public void remove(String key) {
        context.get().remove(key);
    }

    @Override
    public void clear() {
        context.get().clear();
    }

    @Override
    public Map<String, String> getCopyOfContextMap() {
        var map = context.get();
        if (map.isEmpty()) {
            return null;
        }
        return Collections.unmodifiableMap(new HashMap<>(map));
    }

    @Override
    public void setContextMap(Map<String, String> contextMap) {
        context.set(new HashMap<>(contextMap));
    }

    @Override
    public void pushByKey(String key, String value) {
        dequeMap.get().computeIfAbsent(key, k -> new ArrayDeque<>()).push(value);
    }

    @Override
    public String popByKey(String key) {
        Deque<String> deque = dequeMap.get().get(key);
        if (deque != null && !deque.isEmpty()) {
            return deque.pop();
        }
        return null;
    }

    @Override
    public Deque<String> getCopyOfDequeByKey(String key) {
        Deque<String> deque = dequeMap.get().get(key);
        if (deque == null || deque.isEmpty()) {
            return null;
        }
        return new ArrayDeque<>(deque);
    }

    @Override
    public void clearDequeByKey(String key) {
        Deque<String> deque = dequeMap.get().get(key);
        if (deque != null) {
            deque.clear();
        }
    }
}
