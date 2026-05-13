package com.jujin.freeway.ioc.exception;
import com.jujin.freeway.ioc.*;

import com.jujin.freeway.ioc.advisor.LoggingInterceptor;

/**
 * Used by {@link LoggingInterceptor} to track which
 * exceptions have been logged during the current request (the ExceptionTracker
 * is perthread). This keeps redundant information from appearing in the console
 * output.
 */
public interface ExceptionTracker {
    /**
     * Returns true if the indicated exception has already been logged (it is
     * assumed that the exception will be logged if this method returns false). The
     * exception is recorded for later checks.
     *
     * @param exception
     *            to check
     * @return false if the exception has not been previously checked, true
     *         otherwise
     */
    boolean exceptionLogged(Throwable exception);
}
