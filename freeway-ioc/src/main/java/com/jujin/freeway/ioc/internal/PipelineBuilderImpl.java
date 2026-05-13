package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.DefaultServiceProxyBuilder;
import com.jujin.freeway.ioc.advisor.PipelineBuilder;
import com.jujin.freeway.ioc.annotations.Builtin;
import org.slf4j.Logger;

import java.util.List;

public class PipelineBuilderImpl implements PipelineBuilder {

    private final JdkProxyFactory proxyFactory;

    private final DefaultServiceProxyBuilder defaultImplementationBuilder;

    public PipelineBuilderImpl(@Builtin JdkProxyFactory proxyFactory,

        DefaultServiceProxyBuilder defaultImplementationBuilder) {
        this.proxyFactory = proxyFactory;
        this.defaultImplementationBuilder = defaultImplementationBuilder;
    }

    @Override
    public <S, F> S build(Logger logger, Class<S> serviceInterface, Class<F> filterInterface, List<F> filters) {
        S terminator = defaultImplementationBuilder.createDefaultImplementation(serviceInterface);

        return build(logger, serviceInterface, filterInterface, filters, terminator);
    }

    @Override
    public <S, F> S build(Logger logger, Class<S> serviceInterface, Class<F> filterInterface, List<F> filters,
        S terminator) {
        if (filters.isEmpty())
            return terminator;

        BridgeBuilder<S, F> bb = new BridgeBuilder<S, F>(logger, serviceInterface, filterInterface, proxyFactory);

        // The first bridge will point to the terminator.
        // Like service decorators, we work deepest (last)
        // to shallowest (first)

        S next = terminator;
        int count = filters.size();

        for (int i = count - 1; i >= 0; i--) {
            F filter = filters.get(i);

            next = bb.instantiateBridge(next, filter);
        }

        return next;
    }

}
