package com.jujin.freeway.ioc.advisor;

import org.slf4j.Logger;

/**
 * A service used in conjunction with a service advisor method to add logging
 * advice to a service.
 *
 * @see com.jujin.freeway.ioc.annotations.Advise
 */
public interface LoggingAdvisor {
    /**
     * Adds logging advice to all methods of the object.
     *
     * @param logger
     *            log used for debug level logging messages by the interceptor
     * @param methodAdviceReceiver
     */
    void addLoggingAdvice(Logger logger, MethodAdviceReceiver methodAdviceReceiver);
}
