package com.jujin.freeway.ioc.advisor.internal;

import com.jujin.freeway.ioc.advisor.LazyAdvisor;
import com.jujin.freeway.ioc.advisor.MethodAdvice;
import com.jujin.freeway.ioc.advisor.MethodAdviceReceiver;
import com.jujin.freeway.ioc.advisor.MethodInvocation;
import com.jujin.freeway.ioc.advisor.ThunkCreator;
import com.jujin.freeway.ioc.annotations.NotLazy;
import com.jujin.freeway.ioc.annotations.PreventServiceDecoration;
import com.jujin.freeway.ioc.internal.util.StringUtils;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import com.jujin.freeway.ioc.lifecycle.internal.CachingObjectCreator;
import java.lang.reflect.Method;

@PreventServiceDecoration
public class LazyAdvisorImpl implements LazyAdvisor {

    private final ThunkCreator thunkCreator;

    public LazyAdvisorImpl(ThunkCreator thunkCreator) {
        this.thunkCreator = thunkCreator;
    }

    @Override
    public void addLazyMethodInvocationAdvice(
        MethodAdviceReceiver methodAdviceReceiver
    ) {
        for (Method m : methodAdviceReceiver.getInterface().getMethods()) {
            if (filter(m)) addAdvice(m, methodAdviceReceiver);
        }
    }

    private void addAdvice(Method method, MethodAdviceReceiver receiver) {
        final Class<?> thunkType = method.getReturnType();

        final String description = String.format(
            "<%s Thunk for %s>",
            thunkType.getName(),
            StringUtils.asString(method)
        );

        MethodAdvice advice = new MethodAdvice() {
            /**
             * When the method is invoked, we don't immediately proceed. Instead, we return
             * a thunk instance that defers its behavior to the lazily invoked invocation.
             */
            @Override
            public void advise(final MethodInvocation invocation) {
                ObjectCreator<?> deferred = () -> {
                    invocation.proceed();
                    return invocation.getReturnValue();
                };

                ObjectCreator<?> cachingObjectCreator =
                    new CachingObjectCreator<>(deferred);

                Object thunk = thunkCreator.createThunk(
                    thunkType,
                    cachingObjectCreator,
                    description
                );

                invocation.setReturnValue(thunk);
            }
        };

        receiver.adviseMethod(method, advice);
    }

    private boolean filter(Method method) {
        if (method.getAnnotation(NotLazy.class) != null) return false;

        if (!method.getReturnType().isInterface()) return false;

        for (Class<?> extype : method.getExceptionTypes()) {
            if (!RuntimeException.class.isAssignableFrom(extype)) return false;
        }

        return true;
    }
}
