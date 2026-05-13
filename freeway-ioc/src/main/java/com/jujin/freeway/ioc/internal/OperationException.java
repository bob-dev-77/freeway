package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.exception.FreewayException;

/**
 * An exception caught and reported by an
 * {@link com.jujin.freeway.ioc.advisor.OperationTracker}; the trace property
 * identifies what operations were active at the time of the exception.
 */
public class OperationException extends FreewayException {
    private static final long serialVersionUID = -7555673473832355909L;

    private final String[] trace;

    public OperationException(Throwable cause, String[] trace) {
        super(cause.getMessage(), cause);

        this.trace = trace;
    }

    public String[] getTrace() {
        return trace;
    }
}
