package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.advisor.MethodAdvice;
import com.jujin.freeway.ioc.advisor.MethodInvocation;
import com.jujin.freeway.ioc.exception.ExceptionTracker;
import org.slf4j.Logger;

public class LoggingAdvice implements MethodAdvice {
    private final MethodLogger methodLogger;

    public LoggingAdvice(Logger logger, ExceptionTracker exceptionTracker) {
        methodLogger = new MethodLogger(logger, exceptionTracker);
    }

    @Override
    public void advise(MethodInvocation invocation) {
        boolean debug = methodLogger.isDebugEnabled();

        if (!debug) {
            invocation.proceed();
            return;
        }

        methodLogger.entry(invocation);

        try {
            invocation.proceed();
        } catch (RuntimeException ex) {
            methodLogger.fail(invocation, ex);

            throw ex;
        }

        if (invocation.didThrowCheckedException()) {
            Exception thrown = invocation.getCheckedException(Exception.class);

            methodLogger.fail(invocation, thrown);

            return;
        }

        methodLogger.exit(invocation);
    }
}
