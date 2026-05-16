package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.*;
import com.jujin.freeway.ioc.advisor.OperationTracker;
import com.jujin.freeway.ioc.internal.util.InternalUtils;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link ServiceBuilderContext}. We
 * just have one implementation that fills the purposes of methods that need a
 * {@link ServiceContext} (which includes service
 * decorator methods) as well as methods that need a
 * {@link ServiceBuilderContext} (which is just service
 * builder methods). Since it is most commonly used for the former, we'll just
 * leave the name as ServiceContextImpl.
 */
@SuppressWarnings("rawtypes")
public class ServiceContextImpl
    extends ServiceLocatorImpl
    implements ServiceBuilderContext {

    private final InternalRegistry registry;

    private final Module module;

    private final ServiceDefinition serviceDef;

    private final Logger logger;

    private final JdkProxyFactory proxyFactory;

    public ServiceContextImpl(
        InternalRegistry registry,
        Module module,
        ServiceDefinition serviceDef,
        JdkProxyFactory proxyFactory,
        Logger logger) {
        super(registry, module);
        this.registry = registry;
        this.module = module;
        this.serviceDef = serviceDef;
        this.proxyFactory = proxyFactory;
        this.logger = logger;
    }

    @Override
    public String getServiceId() {
        return serviceDef.getServiceId();
    }

    @Override
    public Class getServiceInterface() {
        return serviceDef.getServiceInterface();
    }

    @Override
    public Class getServiceImplementation() {
        return serviceDef.getServiceImplementation();
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public <T> Collection<T> getUnorderedConfiguration(
        final Class<T> valueType) {
        Collection<T> result = registry.invoke(
            "Collecting unordered configuration for service " +
                serviceDef.getServiceId(),
            () -> registry.getUnorderedConfiguration(serviceDef, valueType));

        logConfiguration(result);

        return result;
    }

    private void logConfiguration(Collection configuration) {
        if (logger.isDebugEnabled())
            logger.debug("Constructed configuration: {}", configuration);
    }

    @Override
    public <T> List<T> getOrderedConfiguration(final Class<T> valueType) {
        List<T> result = registry.invoke(
            "Collecting ordered configuration for service " +
                serviceDef.getServiceId(),
            () -> registry.getOrderedConfiguration(serviceDef, valueType));

        logConfiguration(result);

        return result;
    }

    @Override
    public <K, V> Map<K, V> getMappedConfiguration(
        final Class<K> keyType,
        final Class<V> valueType) {
        Map<K, V> result = registry.invoke(
            "Collecting mapped configuration for service " +
                serviceDef.getServiceId(),
            () -> registry.getMappedConfiguration(serviceDef, keyType, valueType));

        if (logger.isDebugEnabled())
            logger.debug("Constructed configuration: {}", result);

        return result;
    }

    @Override
    public Object getInstance() {
        return module.getInstance();
    }

    @Override
    public <T> T autobuild(String description, final Class<T> clazz) {
        assert clazz != null;

        return registry.invoke(description, () -> {
            Constructor constructor = InternalUtils.findAutobuildConstructor(
                clazz);

            if (constructor == null)
                throw new RuntimeException(
                    String.format(
                        "Class %s does not contain a public constructor needed to autobuild.",
                        clazz.getName()));

            String ctorDescription = constructor.toString();

            ObjectCreator creator = new ConstructorServiceCreator(
                ServiceContextImpl.this,
                ctorDescription,
                constructor);

            return clazz.cast(creator.create());
        });
    }

    @Override
    public <T> T autobuild(final Class<T> clazz) {
        assert clazz != null;

        return autobuild(
            "Autobuilding instance of class " + clazz.getName(),
            clazz);
    }

    @Override
    public OperationTracker getTracker() {
        return registry;
    }

    @Override
    public AnnotationProvider getClassAnnotationProvider() {
        return serviceDef.getClassAnnotationProvider();
    }

    @Override
    public AnnotationProvider getMethodAnnotationProvider(
        String methodName,
        Class... parameterTypes) {
        return serviceDef.getMethodAnnotationProvider(
            methodName,
            parameterTypes);
    }
}
