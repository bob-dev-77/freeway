package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.advisor.AspectInterceptor;
import com.jujin.freeway.ioc.advisor.AspectInterceptorBuilder;
import com.jujin.freeway.ioc.advisor.LoggingAdvisor;
import com.jujin.freeway.ioc.advisor.LoggingInterceptor;
import com.jujin.freeway.ioc.annotations.PreventServiceDecoration;
import org.slf4j.Logger;

@PreventServiceDecoration
public class LoggingInterceptorImpl implements LoggingInterceptor {
    private final AspectInterceptor interceptor;

    private final LoggingAdvisor advisor;

    public LoggingInterceptorImpl(AspectInterceptor interceptor, LoggingAdvisor advisor) {
        this.interceptor = interceptor;
        this.advisor = advisor;
    }

    @Override
    public <T> T build(Class<T> serviceInterface, T delegate, String serviceId, final Logger logger) {
        AspectInterceptorBuilder<T> builder = interceptor.createBuilder(serviceInterface, delegate, String.format(
            "<Logging interceptor for %s(%s)>", serviceId, serviceInterface.getName()));
        advisor.addLoggingAdvice(logger, builder);

        return builder.build();
    }

}
