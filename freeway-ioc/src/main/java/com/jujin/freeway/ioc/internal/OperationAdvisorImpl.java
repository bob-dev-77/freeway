package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.advisor.*;
import com.jujin.freeway.ioc.annotations.Operation;
import com.jujin.freeway.ioc.annotations.PreventServiceDecoration;
import com.jujin.freeway.ioc.internal.util.DisplayUtils;
import java.lang.reflect.Method;

@PreventServiceDecoration
public class OperationAdvisorImpl implements OperationAdvisor {

    private final OperationTracker tracker;

    public OperationAdvisorImpl(OperationTracker tracker) {
        this.tracker = tracker;
    }

    private Runnable toRunnable(final MethodInvocation invocation) {
        return () -> invocation.proceed();
    }

    private class SimpleAdvice implements MethodAdvice {

        private final String description;

        SimpleAdvice(String description) {
            this.description = description;
        }

        @Override
        public void advise(MethodInvocation invocation) {
            tracker.run(description, toRunnable(invocation));
        }
    }

    private class FormattedAdvice implements MethodAdvice {

        private final String format;

        FormattedAdvice(String format) {
            this.format = format;
        }

        @Override
        public void advise(MethodInvocation invocation) {
            Object[] parameters = extractParameters(invocation);

            String description = String.format(format, parameters);

            tracker.run(description, toRunnable(invocation));
        }

        private Object[] extractParameters(MethodInvocation invocation) {
            int count = invocation.getMethod().getParameterTypes().length;

            Object[] result = new Object[count];

            for (int i = 0; i < count; i++) {
                result[i] = invocation.getParameter(i);
            }

            return result;
        }
    }

    @Override
    public void addOperationAdvice(MethodAdviceReceiver receiver) {
        for (Method m : receiver.getInterface().getMethods()) {
            Operation annotation = receiver.getMethodAnnotation(
                m,
                Operation.class
            );

            if (annotation != null) {
                String value = annotation.value();

                receiver.adviseMethod(m, createAdvice(value));
            }
        }
    }

    @Override
    public MethodAdvice createAdvice(String description) {
        assert DisplayUtils.isNonBlank(description);

        if (description.contains("%")) {
            return new FormattedAdvice(description);
        }

        return new SimpleAdvice(description);
    }
}
