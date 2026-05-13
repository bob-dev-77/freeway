package com.jujin.freeway.ioc.advisor;

/**
 * Advice that is executed when a method of a service interface is invoked.
 * Replaces {@code com.jujin.freeway.plastic.MethodAdvice}.
 *
 * @see MethodAdviceReceiver
 * @see MethodInvocation
 */
@FunctionalInterface
public interface MethodAdvice {
    /**
     * Invoked to advise the method invocation. The implementation may perform
     * processing before and after calling {@link MethodInvocation#proceed()}.
     *
     * @param invocation
     *            the method invocation to advise
     */
    void advise(MethodInvocation invocation);
}
