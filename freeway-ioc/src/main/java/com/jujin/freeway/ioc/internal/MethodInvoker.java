package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ModuleInstanceSource;
import com.jujin.freeway.ioc.ServiceContext;
import com.jujin.freeway.ioc.ServiceLocator;
import com.jujin.freeway.ioc.advisor.OperationTracker;
import com.jujin.freeway.ioc.internal.util.InjectionContext;
import com.jujin.freeway.ioc.internal.util.InstancePlanBuilder;
import com.jujin.freeway.ioc.internal.util.InternalUtils;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;

/**
 * Helper that encapsulates the logic for invoking a module method with
 * injection resources. Extracted from
 * {@link AbstractMethodInvokingInstrumenter} so that clients can use
 * composition rather than inheritance.
 */
public class MethodInvoker {

    private final ModuleInstanceSource moduleSource;
    private final Method method;
    private final ServiceContext resources;
    private final Logger logger;

    /** Default resources map that callers can copy and augment before invoking. */
    public final Map<Class<?>, Object> resourcesDefaults = new HashMap<>();

    public MethodInvoker(
        ModuleInstanceSource moduleSource,
        Method method,
        ServiceContext resources,
        JdkProxyFactory proxyFactory
    ) {
        this.moduleSource = moduleSource;
        this.method = method;
        this.resources = resources;

        String serviceId = resources.getServiceId();

        resourcesDefaults.put(String.class, serviceId);
        resourcesDefaults.put(ServiceLocator.class, resources);
        resourcesDefaults.put(ServiceContext.class, resources);
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
            : moduleSource.getInstance();
    }

    /**
     * Invokes the module method, resolving parameters from the given injection
     * resources (merged with {@link #resourcesDefaults} by the caller).
     *
     * @param injectionContext
     *            the fully-populated injection resources for this invocation
     * @return the return value of the method invocation
     */
    public Object invoke(InjectionContext injectionContext) {
        String description = String.format("Invoking method %s", toString());

        ObjectCreator<Object> plan = InstancePlanBuilder.buildForMethod(
            resources.getTracker(),
            resources,
            injectionContext,
            logger,
            description,
            getModuleInstance(),
            method
        );

        return plan.create();
    }
}
