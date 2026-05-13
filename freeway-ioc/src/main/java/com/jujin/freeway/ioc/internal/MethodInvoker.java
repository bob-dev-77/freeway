package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ModuleBuilderSource;
import com.jujin.freeway.ioc.ServiceLocator;
import com.jujin.freeway.ioc.ServiceResources;
import com.jujin.freeway.ioc.advisor.OperationTracker;
import com.jujin.freeway.ioc.internal.util.InjectionResources;
import com.jujin.freeway.ioc.internal.util.InternalUtils;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper that encapsulates the logic for invoking a module method with
 * injection resources. Extracted from
 * {@link AbstractMethodInvokingInstrumenter} so that clients can use
 * composition rather than inheritance.
 */
public class MethodInvoker {

    private final ModuleBuilderSource moduleSource;
    private final Method method;
    private final ServiceResources resources;
    private final Logger logger;

    /** Default resources map that callers can copy and augment before invoking. */
    public final Map<Class<?>, Object> resourcesDefaults = new HashMap<>();

    public MethodInvoker(
        ModuleBuilderSource moduleSource,
        Method method,
        ServiceResources resources,
        JdkProxyFactory proxyFactory) {
        this.moduleSource = moduleSource;
        this.method = method;
        this.resources = resources;

        String serviceId = resources.getServiceId();

        resourcesDefaults.put(String.class, serviceId);
        resourcesDefaults.put(ServiceLocator.class, resources);
        resourcesDefaults.put(ServiceResources.class, resources);
        logger = resources.getLogger();
        resourcesDefaults.put(Logger.class, logger);
        Class<?> serviceInterface = resources.getServiceInterface();
        resourcesDefaults.put(Class.class, serviceInterface);
        resourcesDefaults.put(OperationTracker.class, resources.getTracker());
    }

    @Override
    public String toString() {
        return method.toString();
    }

    private Object getModuleInstance() {
        return InternalUtils.isStatic(method)
            ? null
            : moduleSource.getModuleBuilder();
    }

    /**
     * Invokes the module method, resolving parameters from the given injection
     * resources (merged with {@link #resourcesDefaults} by the caller).
     *
     * @param injectionResources
     *            the fully-populated injection resources for this invocation
     * @return the return value of the method invocation
     */
    public Object invoke(InjectionResources injectionResources) {
        String description = String.format("Invoking method %s", toString());

        ObjectCreator<Object> plan = InternalUtils.createMethodInvocationPlan(
            resources.getTracker(),
            resources,
            injectionResources,
            logger,
            description,
            getModuleInstance(),
            method);

        return plan.create();
    }
}
