package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.lifecycle.ObjectCreator;

/**
 * An {@link com.jujin.freeway.ioc.lifecycle.ObjectCreator} that delegates to
 * another {@link com.jujin.freeway.ioc.lifecycle.ObjectCreator} and caches the
 * result.
 */
public class CachingObjectCreator<T> implements ObjectCreator<T> {

    private boolean cached;

    private T cachedValue;

    private ObjectCreator<T> delegate;

    public CachingObjectCreator(ObjectCreator<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public T create() {
        if (!cached) {
            synchronized (this) {
                if (!cached) {
                    cachedValue = delegate.create();
                    cached = true;
                    delegate = null;
                }
            }
        }
        return cachedValue;
    }
}
