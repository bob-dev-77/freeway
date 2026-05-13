package com.jujin.freeway.ioc.internal;

import java.util.List;

import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.annotations.Builtin;
import com.jujin.freeway.ioc.DefaultServiceProxyBuilder;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.advisor.PipelineBuilder;
import org.slf4j.Logger;

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
