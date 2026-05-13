package com.jujin.freeway.ioc.advisor;

import org.slf4j.Logger;

/**
 * Service that can create a logging interceptor that wraps around a service
 * implementation (or interceptor). The interceptor works with the service's log
 * to log, at debug level, method entry (with arguments), method exit (with
 * return value, if any) as well as any thrown exceptions.
 * <p>
 * This represents the Freeway 5.0 decorator approach; for Freeway 5.1 you may
 * want to use the {@link com.jujin.freeway.ioc.advisor.LoggingAdvisor} in
 * conjunction with a service advisor method.
 */
public interface LoggingInterceptor {
    /**
     * Builds a logging interceptor instance.
     *
     * @param <T>
     * @param serviceInterface
     *            interface implemented by the delegate
     * @param delegate
     *            existing object to be wrapped
     * @param serviceId
     *            id of service
     * @param logger
     *            log used for debug level logging messages by the interceptor
     * @return a new object implementing the interface that can be used in place of
     *         the delegate, providing logging behavior around each method call on
     *         the service interface
     */
    <T> T build(Class<T> serviceInterface, T delegate, String serviceId, Logger logger);
}
