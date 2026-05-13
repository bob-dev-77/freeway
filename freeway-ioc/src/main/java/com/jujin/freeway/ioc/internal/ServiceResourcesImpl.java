package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ServiceDefinition;
import com.jujin.freeway.ioc.AnnotationProvider;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import com.jujin.freeway.ioc.advisor.OperationTracker;
import com.jujin.freeway.ioc.ServiceBuilderResources;
import com.jujin.freeway.ioc.internal.util.InternalUtils;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;

/**
 * Implementation of {@link com.jujin.freeway.ioc.ServiceBuilderResources}. We
 * just have one implementation that fills the purposes of methods that need a
 * {@link com.jujin.freeway.ioc.ServiceResources} (which includes service
 * decorator methods) as well as methods that need a
 * {@link com.jujin.freeway.ioc.ServiceBuilderResources} (which is just service
 * builder methods). Since it is most commonly used for the former, we'll just
 * leave the name as ServiceResourcesImpl.
 */
@SuppressWarnings("rawtypes")
public class ServiceResourcesImpl
    extends ServiceLocatorImpl
    implements ServiceBuilderResources {

    private final InternalRegistry registry;

    private final Module module;

    private final ServiceDefinition serviceDef;

    private final Logger logger;

    private final JdkProxyFactory proxyFactory;

    public ServiceResourcesImpl(
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
            logger.debug(
                IOCMessages.constructedConfiguration(configuration));
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
            logger.debug(
                IOCMessages.constructedConfiguration(result));

        return result;
    }

    @Override
    public Object getModuleBuilder() {
        return module.getModuleBuilder();
    }

    @Override
    public <T> T autobuild(String description, final Class<T> clazz) {
        assert clazz != null;

        return registry.invoke(description, () -> {
            Constructor constructor = InternalUtils.findAutobuildConstructor(
                clazz);

            if (constructor == null)
                throw new RuntimeException(
                    IOCMessages.noAutobuildConstructor(clazz));

            String ctorDescription = constructor.toString();

            ObjectCreator creator = new ConstructorServiceCreator(
                ServiceResourcesImpl.this,
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

    public Class getImplementationClass() {
        return null;
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
