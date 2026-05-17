package com.jujin.freeway.ioc;

import com.jujin.freeway.ioc.advisor.AspectInterceptor;
import com.jujin.freeway.ioc.advisor.ChainBuilder;
import com.jujin.freeway.ioc.advisor.LazyAdvisor;
import com.jujin.freeway.ioc.advisor.LoggingAdvisor;
import com.jujin.freeway.ioc.advisor.LoggingInterceptor;
import com.jujin.freeway.ioc.advisor.OperationAdvisor;
import com.jujin.freeway.ioc.advisor.PipelineBuilder;
import com.jujin.freeway.ioc.advisor.StrategyBuilder;
import com.jujin.freeway.ioc.advisor.ThunkCreator;
import com.jujin.freeway.ioc.advisor.internal.AspectDecoratorImpl;
import com.jujin.freeway.ioc.advisor.internal.ChainBuilderImpl;
import com.jujin.freeway.ioc.advisor.internal.DefaultServiceProxyBuilderImpl;
import com.jujin.freeway.ioc.advisor.internal.LazyAdvisorImpl;
import com.jujin.freeway.ioc.advisor.internal.LoggingAdvisorImpl;
import com.jujin.freeway.ioc.advisor.internal.LoggingInterceptorImpl;
import com.jujin.freeway.ioc.advisor.internal.OperationAdvisorImpl;
import com.jujin.freeway.ioc.advisor.internal.PipelineBuilderImpl;
import com.jujin.freeway.ioc.advisor.internal.PropertyAccessImpl;
import com.jujin.freeway.ioc.advisor.internal.PropertyShadowBuilderImpl;
import com.jujin.freeway.ioc.advisor.internal.StrategyBuilderImpl;
import com.jujin.freeway.ioc.advisor.internal.ThunkCreatorImpl;
import com.jujin.freeway.ioc.advisor.internal.UpdateListenerHubImpl;
import com.jujin.freeway.ioc.annotations.Builtin;
import com.jujin.freeway.ioc.annotations.Marker;
import com.jujin.freeway.ioc.property.PropertyAccess;
import com.jujin.freeway.ioc.property.PropertyShadowBuilder;

@Marker(Builtin.class)
public class AdvisorModule {

    public static void bind(ServiceBinder binder) {
        binder.bind(LoggingInterceptor.class, LoggingInterceptorImpl.class);
        binder.bind(ChainBuilder.class, ChainBuilderImpl.class);
        binder.bind(PropertyAccess.class, PropertyAccessImpl.class);
        binder.bind(StrategyBuilder.class, StrategyBuilderImpl.class);
        binder.bind(
            PropertyShadowBuilder.class,
            PropertyShadowBuilderImpl.class
        );
        binder
            .bind(PipelineBuilder.class, PipelineBuilderImpl.class)
            .preventReloading();
        binder.bind(
            DefaultServiceProxyBuilder.class,
            DefaultServiceProxyBuilderImpl.class
        );
        binder.bind(AspectInterceptor.class, AspectDecoratorImpl.class);
        binder.bind(LoggingAdvisor.class, LoggingAdvisorImpl.class);
        binder.bind(LazyAdvisor.class, LazyAdvisorImpl.class);
        binder.bind(ThunkCreator.class, ThunkCreatorImpl.class);
        binder
            .bind(UpdateListenerHub.class, UpdateListenerHubImpl.class)
            .preventReloading();
        binder.bind(OperationAdvisor.class, OperationAdvisorImpl.class);
    }
}
