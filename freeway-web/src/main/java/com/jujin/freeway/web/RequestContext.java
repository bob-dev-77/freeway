package com.jujin.freeway.web;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-request context, scoped to the current thread (virtual thread per request).
 * Accessible from any IoC service via injection during request processing.
 *
 * <p>Bound as a "perthread" service in {@link WebModule}. A filter
 * initializes it at the start of each request and the {@code PerthreadManager}
 * cleans it up at the end.</p>
 */
public interface RequestContext {

    /** Returns the unique correlation ID for this request (auto-generated). */
    String correlationId();

    /** Returns the request start instant. */
    Instant startTime();

    /** Returns the current user principal, or null if not authenticated. */
    Object principal();

    /** Sets the current user principal (e.g., after authentication). */
    void setPrincipal(Object principal);

    /** Returns an arbitrary attribute, or null. */
    Object attribute(String key);

    /** Stores an arbitrary attribute for the lifetime of this request. */
    void setAttribute(String key, Object value);

    /** Creates a new context with a fresh correlation ID and current timestamp. */
    static RequestContext create() {
        return new DefaultRequestContext(
            UUID.randomUUID().toString().replace("-", ""),
            Instant.now());
    }
}
