package com.jujin.freeway.boot.test.services;

import javax.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demonstrates constructor injection — Greeter is injected automatically.
 */
public class StoreImpl implements Store {

    private final Greeter greeter;
    private final ConcurrentHashMap<String, String> storage = new ConcurrentHashMap<>();

    @Inject
    public StoreImpl(Greeter greeter) {
        this.greeter = greeter;
    }

    @Override
    public void put(String key, String value) {
        storage.put(key, greeter.greet(value));
    }

    @Override
    public String get(String key) {
        return storage.get(key);
    }

    @Override
    public Map<String, String> snapshot() {
        return new LinkedHashMap<>(storage);
    }
}
