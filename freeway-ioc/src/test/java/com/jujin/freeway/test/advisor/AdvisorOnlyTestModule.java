package com.jujin.freeway.test.advisor;

import com.jujin.freeway.ioc.advisor.MethodAdvice;
import com.jujin.freeway.ioc.advisor.MethodAdviceReceiver;
import com.jujin.freeway.ioc.advisor.MethodInvocation;
import com.jujin.freeway.ioc.ServiceBinder;
import com.jujin.freeway.ioc.annotations.Advise;

import java.lang.reflect.Method;

/**
 * Module demonstrating that {@code @Advise} alone (without {@code @Decorate}) can
 * achieve the same service-level wrapping, by stacking multiple MethodAdvice
 * entries on each method.
 */
public class AdvisorOnlyTestModule {

    public static void bind(ServiceBinder binder) {
        binder.bind(Greeter.class, GreeterImpl.class).withId("Greeter");
    }

    /**
     * Registers per-method advice for the Greeter interface using only @Advise.
     * Bracket-wrap (outer) and uppercase (inner) are stacked to replicate the
     * Decorator + Advise combination from AdvisorTestModule.
     */
    @Advise(serviceInterface = Greeter.class)
    public static void adviseAllMethods(MethodAdviceReceiver receiver) {
        Class<?> iface = receiver.getInterface();
        try {
            Method greetMethod = iface.getMethod("greet", String.class);
            Method farewellMethod = iface.getMethod("farewell", String.class);

            // Stack order: first registered = outermost
            // greet: BracketWrap(UpperCaseName(greet(name)))
            receiver.adviseMethod(greetMethod, BracketWrapAdvice.INSTANCE);
            receiver.adviseMethod(greetMethod, UpperCaseNameAdvice.INSTANCE);

            // farewell: BracketWrap(UpperCaseName(farewell(name)))
            receiver.adviseMethod(farewellMethod, BracketWrapAdvice.INSTANCE);
            receiver.adviseMethod(farewellMethod, UpperCaseNameAdvice.INSTANCE);

            // countChars intentionally left unadvised — the proxy passes through
            // directly, matching the decorator's pass-through behavior.
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Outer advice that wraps String return values in brackets, replicating
     * what @Decorate does at the service level.
     */
    private static final class BracketWrapAdvice implements MethodAdvice {
        static final BracketWrapAdvice INSTANCE = new BracketWrapAdvice();

        @Override
        public void advise(MethodInvocation invocation) {
            invocation.proceed();
            Object result = invocation.getReturnValue();
            if (result instanceof String) {
                invocation.setReturnValue("[" + result + "]");
            }
        }
    }

    /**
     * Inner advice that uppercases the first String parameter before proceeding.
     */
    private static final class UpperCaseNameAdvice implements MethodAdvice {
        static final UpperCaseNameAdvice INSTANCE = new UpperCaseNameAdvice();

        @Override
        public void advise(MethodInvocation invocation) {
            String name = invocation.getParameter(0).toString();
            invocation.setParameter(0, name.toUpperCase());
            invocation.proceed();
        }
    }
}
