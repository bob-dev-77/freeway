package com.jujin.freeway.ioc.advisor.internal;

import com.jujin.freeway.ioc.advisor.LoggingAdvisor;
import com.jujin.freeway.ioc.advisor.MethodAdvice;
import com.jujin.freeway.ioc.advisor.MethodAdviceReceiver;
import com.jujin.freeway.ioc.annotations.PreventServiceDecoration;
import com.jujin.freeway.ioc.exception.ExceptionTracker;
import org.slf4j.Logger;

@PreventServiceDecoration
public class LoggingAdvisorImpl implements LoggingAdvisor {

    private final ExceptionTracker exceptionTracker;

    public LoggingAdvisorImpl(ExceptionTracker exceptionTracker) {
        this.exceptionTracker = exceptionTracker;
    }

    @Override
    public void addLoggingAdvice(Logger logger, MethodAdviceReceiver receiver) {
        MethodAdvice advice = new LoggingAdvice(logger, exceptionTracker);

        receiver.adviseAllMethods(advice);
    }
}
