package com.jujin.freeway.ioc.internal.util;

import org.slf4j.Logger;
import java.util.function.Supplier;

/**
 */
public class LoggingInvokableWrapper<T> implements Supplier<T> {
    private final Logger logger;

    private final String message;

    private final Supplier<T> delegate;

    public LoggingInvokableWrapper(Logger logger, String message, Supplier<T> delegate) {
        this.logger = logger;
        this.message = message;
        this.delegate = delegate;
    }

    @Override
    public T get() {
        logger.debug(message);

        return delegate.get();
    }
}
