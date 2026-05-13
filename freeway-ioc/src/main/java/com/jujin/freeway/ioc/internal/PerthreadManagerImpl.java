package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.RegistryShutdownHub;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import com.jujin.freeway.ioc.threading.PerThreadValue;
import com.jujin.freeway.ioc.threading.PerthreadManager;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class PerthreadManagerImpl implements PerthreadManager {
    private final PerThreadValue<List<Runnable>> callbacksValue;

    private static final ScopedValue<Map> PER_THREAD_MAP = ScopedValue.newInstance();

    private final Logger logger;

    private final AtomicInteger uuidGenerator = new AtomicInteger();

    private volatile boolean shutdown = false;

    public PerthreadManagerImpl(Logger logger) {
        this.logger = logger;

        callbacksValue = createValue();
    }

    public void registerForShutdown(RegistryShutdownHub hub) {
        hub.addRegistryShutdownListener((Runnable) () -> {
            cleanup();
            shutdown = true;
        });
    }

    private Map getPerthreadMap() {
        if (shutdown) {
            return new HashMap<>();
        }

        if (PER_THREAD_MAP.isBound()) {
            return PER_THREAD_MAP.get();
        }

        // No scope active; return a throwaway map (should not happen in normal
        // operation)
        return new HashMap<>();
    }

    private List<Runnable> getCallbacks() {
        List<Runnable> result = callbacksValue.get();

        if (result == null) {
            result = new ArrayList<>();
            callbacksValue.set(result);
        }

        return result;
    }

    @Override
    public void addThreadCleanupCallback(Runnable callback) {
        assert callback != null;

        getCallbacks().add(callback);
    }

    @Override
    public void cleanup() {
        List<Runnable> callbacks = getCallbacks();

        callbacksValue.set(null);

        for (Runnable callback : callbacks) {
            try {
                callback.run();
            } catch (Exception ex) {
                logger.warn("Error invoking callback {}: {}", callback, ex, ex);
            }
        }
    }

    private static Object NULL_VALUE = new Object();

    <T> ObjectCreator<T> createValue(final Object key, final ObjectCreator<T> delegate) {
        return new DefaultObjectCreator<T>(key, delegate);
    }

    public <T> ObjectCreator<T> createValue(ObjectCreator<T> delegate) {
        return createValue(uuidGenerator.getAndIncrement(), delegate);
    }

    <T> PerThreadValue<T> createValue(final Object key) {
        return new DefaultPerThreadValue(key);
    }

    @Override
    public <T> PerThreadValue<T> createValue() {
        return createValue(uuidGenerator.getAndIncrement());
    }

    @Override
    public void run(Runnable runnable) {
        assert runnable != null;

        if (PER_THREAD_MAP.isBound()) {
            try {
                runnable.run();
            } finally {
                cleanup();
            }
        } else {
            ScopedValue.where(PER_THREAD_MAP, new HashMap<>()).run(() -> {
                try {
                    runnable.run();
                } finally {
                    cleanup();
                }
            });
        }
    }

    @Override
    public <T> T invoke(Supplier<T> invokable) {
        if (PER_THREAD_MAP.isBound()) {
            try {
                return invokable.get();
            } finally {
                cleanup();
            }
        } else {
            return ScopedValue.where(PER_THREAD_MAP, new HashMap<>()).call(() -> {
                try {
                    return invokable.get();
                } finally {
                    cleanup();
                }
            });
        }
    }

    private final class DefaultPerThreadValue<T> implements PerThreadValue<T> {
        private final Object key;

        DefaultPerThreadValue(final Object key) {
            this.key = key;

        }

        @Override
        public T get() {
            return get(null);
        }

        @Override
        public T get(T defaultValue) {
            Map map = getPerthreadMap();

            Object storedValue = map.get(key);

            if (storedValue == null) {
                return defaultValue;
            }

            if (storedValue == NULL_VALUE) {
                return null;
            }

            return (T) storedValue;
        }

        @Override
        public T set(T newValue) {
            getPerthreadMap().put(key, newValue == null ? NULL_VALUE : newValue);

            return newValue;
        }

        @Override
        public boolean exists() {
            return getPerthreadMap().containsKey(key);
        }
    }

    private final class DefaultObjectCreator<T> implements ObjectCreator<T> {

        private final Object key;
        private final ObjectCreator<T> delegate;

        DefaultObjectCreator(final Object key, final ObjectCreator<T> delegate) {
            this.key = key;
            this.delegate = delegate;
        }

        public T create() {
            Map map = getPerthreadMap();
            T storedValue = (T) map.get(key);

            if (storedValue != null) {
                return (storedValue == NULL_VALUE) ? null : storedValue;
            }

            T newValue = delegate.create();

            map.put(key, newValue == null ? NULL_VALUE : newValue);

            return newValue;
        }
    }
}
