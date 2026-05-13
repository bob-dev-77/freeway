package com.jujin.freeway.ioc.internal;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.slf4j.Logger;

/**
 * Used by the {@link com.jujin.freeway.ioc.internal.PipelineBuilderImpl} to
 * create bridge classes and to create instances of bridge classes. A bridge
 * class implements the <em>service</em> interface. Within the chain, bridge 1
 * is passed to filter 1. Invoking methods on bridge 1 will invoke methods on
 * filter 2.
 */
public class BridgeBuilder<S, F> {

    private final Logger logger;

    private final Class<S> serviceInterface;

    private final Class<F> filterInterface;

    private final FilterMethodAnalyzer filterMethodAnalyzer;

    private final JdkProxyFactory proxyFactory;

    public BridgeBuilder(
        Logger logger,
        Class<S> serviceInterface,
        Class<F> filterInterface,
        JdkProxyFactory proxyFactory) {
        this.logger = logger;
        this.serviceInterface = serviceInterface;
        this.filterInterface = filterInterface;

        this.proxyFactory = proxyFactory;

        filterMethodAnalyzer = new FilterMethodAnalyzer(serviceInterface);
    }

    /**
     * Instantiates a bridge object.
     *
     * @param nextBridge
     *            the next Bridge object in the pipeline, or the terminator service
     * @param filter
     *            the filter object for this step of the pipeline
     */
    @SuppressWarnings("unchecked")
    public S instantiateBridge(S nextBridge, F filter) {
        return (S) Proxy.newProxyInstance(
            proxyFactory.getClassLoader(),
            new Class[]{ serviceInterface },
            new BridgeInvocationHandler(nextBridge, filter));
    }

    private class BridgeInvocationHandler implements InvocationHandler {

        private final S nextBridge;
        private final F filter;

        BridgeInvocationHandler(S nextBridge, F filter) {
            this.nextBridge = nextBridge;
            this.filter = filter;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> String.format(
                        "<PipelineBridge from %s to %s>",
                        serviceInterface.getName(),
                        filterInterface.getName());
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> method.invoke(this, args);
                };
            }

            // Find matching method in filter interface
            List<MethodSignature> serviceMethods = new ArrayList<>();
            List<MethodSignature> filterMethods = new ArrayList<>();

            MethodIterator mi = new MethodIterator(serviceInterface);
            while (mi.hasNext()) {
                serviceMethods.add(mi.next());
            }

            mi = new MethodIterator(filterInterface);
            while (mi.hasNext()) {
                filterMethods.add(mi.next());
            }

            MethodSignature ms = null;
            for (MethodSignature sms : serviceMethods) {
                if (sms.getMethod().equals(method)) {
                    ms = sms;
                    break;
                }
            }

            if (ms == null) {
                String message = String.format(
                    "Method %s has no match in filter interface %s.",
                    method,
                    filterInterface.getName());
                logger.error(message);
                throw new RuntimeException(message);
            }

            // Find matching filter method
            Iterator<MethodSignature> iter = filterMethods.iterator();
            while (iter.hasNext()) {
                MethodSignature fms = iter.next();
                int position = filterMethodAnalyzer.findServiceInterfacePosition(ms, fms);

                if (position >= 0) {
                    // Invoke filter method with nextBridge inserted at the correct position
                    Class<?>[] fParamTypes = fms.getParameterTypes();
                    Object[] filterArgs = new Object[fParamTypes.length];
                    int argIndex = 0;

                    for (int i = 0; i < fParamTypes.length; i++) {
                        if (i == position) {
                            filterArgs[i] = nextBridge;
                        } else {
                            filterArgs[i] = args[argIndex++];
                        }
                    }

                    return fms.getMethod().invoke(filter, filterArgs);
                }
            }

            String message = String.format(
                "Method %s has no match in filter interface %s.",
                ms,
                filterInterface.getName());
            logger.error(message);
            throw new RuntimeException(message);
        }
    }
}
