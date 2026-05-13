package com.jujin.freeway.ioc.advisor;

/**
 * A builder may be obtained from the
 * {@link AspectInterceptor} and allows more
 * controlled creation of the created interceptor; it allows different methods
 * to be given different advice, and allows methods to be omitted (in which case
 * the method invocation passes through without advice).
 */
public interface AspectInterceptorBuilder<T> extends MethodAdviceReceiver {

    /**
     * Builds and returns the interceptor. Any methods that have not been advised
     * will become "pass thrus".
     *
     * @return the interceptor instance.
     */
    T build();
}
