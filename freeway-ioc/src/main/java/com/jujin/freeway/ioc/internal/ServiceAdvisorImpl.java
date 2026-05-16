package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ModuleInstanceSource;
import com.jujin.freeway.ioc.ServiceContext;
import com.jujin.freeway.ioc.advisor.MethodAdviceReceiver;
import com.jujin.freeway.ioc.advisor.ServiceAdvisor;
import com.jujin.freeway.ioc.internal.util.InjectionContext;
import com.jujin.freeway.ioc.internal.util.MappedInjectionContext;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class ServiceAdvisorImpl implements ServiceAdvisor {

    private final MethodInvoker invoker;

    public ServiceAdvisorImpl(
        ModuleInstanceSource moduleSource,
        Method method,
        ServiceContext resources,
        JdkProxyFactory proxyFactory) {
        this.invoker = new MethodInvoker(
            moduleSource,
            method,
            resources,
            proxyFactory);
    }

    /**
     * Invokes the configured method, passing the builder. The method will always
     * take, as a parameter, a MethodAdvisor.
     */
    @Override
    public void advise(MethodAdviceReceiver methodAdviceReceiver) {
        Map<Class<?>, Object> resources = new HashMap<>(
            this.invoker.resourcesDefaults);

        resources.put(MethodAdviceReceiver.class, methodAdviceReceiver);

        InjectionContext injectionContext = new MappedInjectionContext(
            resources);

        // By design, advise methods return void, so we know that the return value is
        // null.

        invoker.invoke(injectionContext);
    }
}
