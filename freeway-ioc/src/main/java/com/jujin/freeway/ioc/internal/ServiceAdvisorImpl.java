package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.advisor.MethodAdviceReceiver;
import com.jujin.freeway.ioc.ModuleBuilderSource;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.advisor.ServiceAdvisor;
import com.jujin.freeway.ioc.ServiceResources;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.internal.util.InjectionResources;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.internal.util.MapInjectionResources;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class ServiceAdvisorImpl implements ServiceAdvisor {

    private final MethodInvoker invoker;

    public ServiceAdvisorImpl(
        ModuleBuilderSource moduleSource,
        Method method,
        ServiceResources resources,
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

        InjectionResources injectionResources = new MapInjectionResources(
            resources);

        // By design, advise methods return void, so we know that the return value is
        // null.

        invoker.invoke(injectionResources);
    }
}
