package com.jujin.freeway.web;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class DefaultRequestContext implements RequestContext {

    private final String correlationId;
    private final Instant startTime;
    private Object principal;
    private final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();

    /** No-arg constructor for perthread service instantiation — auto-generates ID and time. */
    public DefaultRequestContext() {
        this.correlationId = UUID.randomUUID().toString().replace("-", "");
        this.startTime = Instant.now();
    }

    public DefaultRequestContext(String correlationId, Instant startTime) {
        this.correlationId = correlationId;
        this.startTime = startTime;
    }

    @Override
    public String correlationId() {
        return correlationId;
    }

    @Override
    public Instant startTime() {
        return startTime;
    }

    @Override
    public Object principal() {
        return principal;
    }

    @Override
    public void setPrincipal(Object principal) {
        this.principal = principal;
    }

    @Override
    public Object attribute(String key) {
        return attributes.get(key);
    }

    @Override
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
}
