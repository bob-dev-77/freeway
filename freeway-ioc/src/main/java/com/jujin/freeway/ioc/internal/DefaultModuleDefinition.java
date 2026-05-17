package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ModuleDefinition;
import com.jujin.freeway.ioc.ServiceBinder;
import com.jujin.freeway.ioc.ServiceBuilderContext;
import com.jujin.freeway.ioc.ServiceDefinition;
import com.jujin.freeway.ioc.advisor.AdvisorDefinition;
import com.jujin.freeway.ioc.advisor.MethodAdviceReceiver;
import com.jujin.freeway.ioc.advisor.internal.AdvisorDefinitionImpl;
import com.jujin.freeway.ioc.annotations.*;
import com.jujin.freeway.ioc.annotations.Optional;
import com.jujin.freeway.ioc.config.Configuration;
import com.jujin.freeway.ioc.config.ContributionDef;
import com.jujin.freeway.ioc.config.MappedConfiguration;
import com.jujin.freeway.ioc.config.OrderedConfiguration;
import com.jujin.freeway.ioc.exception.FreewayException;
import com.jujin.freeway.ioc.internal.util.ReflectionUtils;
import com.jujin.freeway.ioc.internal.util.Scopes;
import com.jujin.freeway.ioc.internal.util.StringUtils;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import com.jujin.freeway.ioc.lifecycle.StartupDef;
import com.jujin.freeway.ioc.lifecycle.internal.ObjectCreatorFactory;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;

/**
 * Starting from the Class for a module, identifies all the services (service
 * builder methods), advisors (service advisor methods) and contributions
 * (service contributor methods).
 */
public class DefaultModuleDefinition
    implements ModuleDefinition, ServiceDefAccumulator
{

    /**
     * The prefix used to identify service builder methods.
     */
    private static final String BUILD_METHOD_NAME_PREFIX = "build";

    /**
     * The prefix used to identify service contribution methods.
     */
    private static final String CONTRIBUTE_METHOD_NAME_PREFIX = "contribute";

    private static final String ADVISE_METHOD_NAME_PREFIX = "advise";

    private static final Map<
        Class<?>,
        ConfigurationType
    > PARAMETER_TYPE_TO_CONFIGURATION_TYPE = new HashMap<>();

    private final Class<?> moduleClass;

    private final Logger logger;

    private final JdkProxyFactory proxyFactory;

    /**
     * Keyed on service id.
     */
    private final Map<String, ServiceDefinition> serviceDefs = new TreeMap<>(
        String.CASE_INSENSITIVE_ORDER
    );

    private final Map<String, AdvisorDefinition> advisorDefs = new TreeMap<>(
        String.CASE_INSENSITIVE_ORDER
    );

    private final Set<ContributionDef> contributionDefs = new HashSet<>();

    private final Set<Class<?>> defaultMarkers = new HashSet<>();

    private final Set<StartupDef> startups = new HashSet<>();

    private static final Set<Method> OBJECT_METHODS = new HashSet<>(
        Arrays.asList(Object.class.getMethods())
    );

    static {
        PARAMETER_TYPE_TO_CONFIGURATION_TYPE.put(
            Configuration.class,
            ConfigurationType.UNORDERED
        );
        PARAMETER_TYPE_TO_CONFIGURATION_TYPE.put(
            OrderedConfiguration.class,
            ConfigurationType.ORDERED
        );
        PARAMETER_TYPE_TO_CONFIGURATION_TYPE.put(
            MappedConfiguration.class,
            ConfigurationType.MAPPED
        );
    }

    /**
     * @param moduleClass
     *            the class that is responsible for building services, etc.
     * @param logger
     *            based on the class name of the module
     * @param proxyFactory
     *            factory used to create proxy classes at runtime
     */
    public DefaultModuleDefinition(
        Class<?> moduleClass,
        Logger logger,
        JdkProxyFactory proxyFactory
    ) {
        this.moduleClass = moduleClass;
        this.logger = logger;
        this.proxyFactory = proxyFactory;

        Marker annotation = moduleClass.getAnnotation(Marker.class);

        if (annotation != null) {
            ReflectionUtils.validateMarkerAnnotations(annotation.value());
            for (Class<?> c : annotation.value()) {
                defaultMarkers.add(c);
            }
        }

        // Want to verify that every public method is meaningful to Freeway IoC.
        // Remaining methods
        // might
        // have typos, i.e., "createFoo" that should be "buildFoo".

        Set<Method> methods;
        try {
            methods = new HashSet<>(Arrays.asList(moduleClass.getMethods()));
        } catch (Exception e) {
            throw new FreewayException(
                "Exception while processing module class " +
                    moduleClass.getName() +
                    ": " +
                    e.getMessage(),
                e
            );
        }

        var methodIterator = methods.iterator();

        while (methodIterator.hasNext()) {
            var method = methodIterator.next();
            for (Method objectMethod : OBJECT_METHODS) {
                if (signaturesAreEqual(method, objectMethod)) {
                    methodIterator.remove();
                    break;
                }
            }
        }

        removeSyntheticMethods(methods);
        removeGroovyObjectMethods(methods);

        boolean modulePreventsServiceDecoration =
            moduleClass.getAnnotation(PreventServiceDecoration.class) != null;

        grind(methods, modulePreventsServiceDecoration);
        bind(methods, modulePreventsServiceDecoration);

        if (methods.isEmpty()) return;

        throw new RuntimeException(
            String.format(
                "Module class %s contains unrecognized public methods: %s.",
                moduleClass.getName(),
                StringUtils.joinSorted(methods)
            )
        );
    }

    private static boolean signaturesAreEqual(Method m1, Method m2) {
        if (!m1.getName().equals(m2.getName())) {
            return false;
        }
        if (!m1.getReturnType().equals(m2.getReturnType())) {
            return false;
        }
        Class<?>[] params1 = m1.getParameterTypes();
        Class<?>[] params2 = m2.getParameterTypes();
        if (params1.length != params2.length) {
            return false;
        }
        for (int i = 0; i < params1.length; i++) {
            if (!params1[i].equals(params2[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Identifies the module class and a list of service ids within the module.
     */
    @Override
    public String toString() {
        return String.format(
            "ModuleDefinition[%s %s]",
            moduleClass.getName(),
            StringUtils.joinSorted(serviceDefs.keySet())
        );
    }

    @Override
    public Class<?> getBuilderClass() {
        return moduleClass;
    }

    @Override
    public Set<String> getServiceIds() {
        return serviceDefs.keySet();
    }

    @Override
    public ServiceDefinition getServiceDef(String serviceId) {
        return serviceDefs.get(serviceId);
    }

    private void removeGroovyObjectMethods(Set<Method> methods) {
        var iterator = methods.iterator();

        while (iterator.hasNext()) {
            var m = iterator.next();
            final String name = m.getName();

            if (
                m
                    .getDeclaringClass()
                    .getName()
                    .equals("groovy.lang.GroovyObject") ||
                (name.equals("getMetaClass") &&
                    m
                        .getReturnType()
                        .getName()
                        .equals("groovy.lang.MetaClass")) ||
                (m.getParameterCount() == 1 &&
                    m
                        .getParameterTypes()[0].getName()
                        .equals("groovy.lang.MetaClass"))
            ) {
                iterator.remove();
            }
        }
    }

    private void removeSyntheticMethods(Set<Method> methods) {
        var iterator = methods.iterator();

        while (iterator.hasNext()) {
            var m = iterator.next();

            if (m.isSynthetic() || m.getName().startsWith("$")) {
                iterator.remove();
            }
        }
    }

    private void grind(
        Set<Method> remainingMethods,
        boolean modulePreventsServiceDecoration
    ) {
        Method[] methods = moduleClass.getMethods();

        Comparator<Method> c = new Comparator<Method>() {
            // By name, ascending, then by parameter count, descending.

            @Override
            public int compare(Method o1, Method o2) {
                int result = o1.getName().compareTo(o2.getName());

                if (result == 0) result =
                    o2.getParameterTypes().length -
                    o1.getParameterTypes().length;

                return result;
            }
        };

        Arrays.sort(methods, c);

        for (Method m : methods) {
            String name = m.getName();

            if (name.startsWith(BUILD_METHOD_NAME_PREFIX)) {
                addServiceDef(m, modulePreventsServiceDecoration);
                remainingMethods.remove(m);
                continue;
            }

            if (
                name.startsWith(CONTRIBUTE_METHOD_NAME_PREFIX) ||
                m.isAnnotationPresent(Contribute.class)
            ) {
                addContributionDef(m);
                remainingMethods.remove(m);
                continue;
            }

            if (
                name.startsWith(ADVISE_METHOD_NAME_PREFIX) ||
                m.isAnnotationPresent(Advise.class)
            ) {
                addAdvisorDef(m);
                remainingMethods.remove(m);
                continue;
            }

            if (m.isAnnotationPresent(Startup.class)) {
                addStartupDef(m);
                remainingMethods.remove(m);
                continue;
            }
        }
    }

    private void addStartupDef(Method method) {
        startups.add(new StartupDefImpl(method));
    }

    private void addContributionDef(Method method) {
        var annotation = method.getAnnotation(Contribute.class);

        Class<?> serviceInterface =
            annotation == null ? null : annotation.value();

        String serviceId =
            annotation != null
                ? null
                : stripMethodPrefix(method, CONTRIBUTE_METHOD_NAME_PREFIX);

        Class<?> returnType = method.getReturnType();
        if (!returnType.equals(void.class)) {
            throw new RuntimeException(
                String.format(
                    "Method %s is named like a service contributor method, but the return type (%s) is not appropriate (it should be void). The return value will be ignored.",
                    StringUtils.asString(method),
                    StringUtils.toSimpleTypeName(returnType)
                )
            );
        }

        ConfigurationType type = null;

        for (Class<?> parameterType : method.getParameterTypes()) {
            ConfigurationType thisParameter =
                PARAMETER_TYPE_TO_CONFIGURATION_TYPE.get(parameterType);

            if (thisParameter != null) {
                if (type != null) throw new RuntimeException(
                    String.format(
                        "Service contribution method %s contains more than one parameter of type Configuration, OrderedConfiguration, or MappedConfiguration. Exactly one such parameter is required for a service contribution method.",
                        StringUtils.asString(method)
                    )
                );

                type = thisParameter;
            }
        }

        if (type == null) throw new RuntimeException(
            String.format(
                "Service contribution method %s does not contain a parameter of type Configuration, OrderedConfiguration or MappedConfiguration. This parameter is how the method make contributions into the service's configuration.",
                StringUtils.asString(method)
            )
        );

        var markers = extractMarkers(method, Contribute.class, Optional.class);

        boolean optional = method.getAnnotation(Optional.class) != null;

        ContributionDef def = new ContributionDefImpl(
            serviceId,
            method,
            optional,
            proxyFactory,
            serviceInterface,
            markers
        );

        contributionDefs.add(def);
    }

    private <T extends Annotation> String[] extractPatterns(
        String id,
        Method method
    ) {
        return new String[] { id };
    }

    private String[] extractConstraints(Method method) {
        var order = method.getAnnotation(Order.class);

        if (order == null) return null;

        return order.value();
    }

    private void addAdvisorDef(Method method) {
        var annotation = method.getAnnotation(Advise.class);

        Class<?> serviceInterface =
            annotation == null ? null : annotation.serviceInterface();

        // TODO: methods just named "decorate"

        String advisorId =
            annotation == null
                ? stripMethodPrefix(method, ADVISE_METHOD_NAME_PREFIX)
                : extractId(serviceInterface, annotation.id());

        // Check for duplicate advisor IDs - fail fast
        if (advisorDefs.containsKey(advisorId)) {
            AdvisorDefinition existing = advisorDefs.get(advisorId);
            throw new RuntimeException(
                String.format(
                    "Duplicate advisor id '%s' in module %s.\n" +
                        "Previous definition: %s\n" +
                        "New definition: %s",
                    advisorId,
                    moduleClass.getName(),
                    existing.toString(),
                    StringUtils.asString(method)
                )
            );
        }

        Class<?> returnType = method.getReturnType();

        if (!returnType.equals(void.class)) throw new RuntimeException(
            String.format(
                "Advise method %s does not return void.",
                toString(method)
            )
        );

        boolean found = false;

        for (Class<?> pt : method.getParameterTypes()) {
            if (pt.equals(MethodAdviceReceiver.class)) {
                found = true;

                break;
            }
        }

        if (!found) throw new RuntimeException(
            String.format(
                "Advise method %s must take a parameter of type %s.",
                toString(method),
                MethodAdviceReceiver.class.getName()
            )
        );

        var markers = extractMarkers(method, Advise.class);

        var def = new AdvisorDefinitionImpl(
            method,
            extractPatterns(advisorId, method),
            extractConstraints(method),
            proxyFactory,
            advisorId,
            serviceInterface,
            markers
        );

        advisorDefs.put(advisorId, def);
    }

    private String extractId(Class<?> serviceInterface, String id) {
        return StringUtils.isBlank(id) ? serviceInterface.getSimpleName() : id;
    }

    private String toString(Method method) {
        return StringUtils.asString(method);
    }

    private String stripMethodPrefix(Method method, String prefix) {
        return method.getName().substring(prefix.length());
    }

    /**
     * Invoked for public methods that have the proper prefix.
     */
    private void addServiceDef(
        final Method method,
        boolean modulePreventsServiceDecoration
    ) {
        var serviceId = ReflectionUtils.getServiceId(method);

        if (serviceId == null) {
            serviceId = stripMethodPrefix(method, BUILD_METHOD_NAME_PREFIX);
        }

        // If the method name was just "build()", then work from the return type.

        if (serviceId.equals("")) serviceId = method
            .getReturnType()
            .getSimpleName();

        // Any number of parameters is fine, we'll adapt. Eventually we have to check
        // that we can satisfy the parameters requested. Thrown exceptions of the method
        // will be caught and wrapped, so we don't need to check those. But we do need a
        // proper
        // return type.

        Class<?> returnType = method.getReturnType();

        if (
            returnType.isPrimitive() || returnType.isArray()
        ) throw new RuntimeException(
            String.format(
                "Method %s is named like a service builder method, but the return type (%s) is not acceptable (try an interface).",
                StringUtils.asString(method),
                method.getReturnType().getCanonicalName()
            )
        );

        String scope = extractServiceScope(method);
        boolean eagerLoad = method.isAnnotationPresent(EagerLoad.class);

        boolean preventDecoration =
            modulePreventsServiceDecoration ||
            method.getAnnotation(PreventServiceDecoration.class) != null;

        ObjectCreatorFactory source = new ObjectCreatorFactory() {
            @Override
            public ObjectCreator<?> construct(ServiceBuilderContext resources) {
                return new ServiceBuilderMethodInvoker(
                    resources,
                    description(),
                    method
                );
            }

            @Override
            public String description() {
                return DefaultModuleDefinition.this.toString(method);
            }
        };

        var markers = new HashSet<>(defaultMarkers);
        markers.addAll(extractServiceDefMarkers(method));

        if (method.getAnnotation(Primary.class) != null) {
            markers.add(Primary.class);
        }

        var serviceDef = new ServiceDefinitionImpl(
            returnType,
            null,
            serviceId,
            markers,
            scope,
            eagerLoad,
            preventDecoration,
            source
        );

        addServiceDef(serviceDef);
    }

    private Collection<Class<?>> extractServiceDefMarkers(Method method) {
        var annotation = method.getAnnotation(Marker.class);

        if (annotation == null) return Collections.emptyList();

        var result = new ArrayList<Class<?>>();
        for (Class<?> c : annotation.value()) result.add(c);
        return result;
    }

    private Set<Class<?>> extractMarkers(
        Method method,
        final Class<?>... annotationClassesToSkip
    ) {
        return Arrays.stream(method.getAnnotations())
            .map(Annotation::annotationType)
            .filter(element -> {
                for (Class<?> skip : annotationClassesToSkip) {
                    if (skip.equals(element)) {
                        return false;
                    }
                }

                return true;
            })
            .collect(Collectors.toSet());
    }

    @Override
    public void addServiceDef(ServiceDefinition serviceDef) {
        var serviceId = serviceDef.getServiceId();

        var existing = serviceDefs.get(serviceId);

        if (existing != null) throw new RuntimeException(
            String.format(
                "Service %s (defined by %s) conflicts with previously defined service defined by %s.",
                serviceId,
                serviceDef.toString(),
                existing.toString()
            )
        );

        serviceDefs.put(serviceId, serviceDef);
    }

    private String extractServiceScope(Method method) {
        var scope = method.getAnnotation(Scope.class);

        return scope != null ? scope.value() : Scopes.SINGLETON;
    }

    @Override
    public Set<ContributionDef> getContributionDefs() {
        return contributionDefs;
    }

    @Override
    public String getLoggerName() {
        return moduleClass.getName();
    }

    /**
     * See if the build class defined a bind method and invoke it.
     *
     * @param remainingMethods
     *            set of methods as yet unaccounted for
     * @param modulePreventsServiceDecoration
     *            true if
     *            {@link com.jujin.freeway.ioc.annotations.PreventServiceDecoration}
     *            on module class
     */
    private void bind(
        Set<Method> remainingMethods,
        boolean modulePreventsServiceDecoration
    ) {
        Throwable failure;
        Method bindMethod = null;

        try {
            bindMethod = moduleClass.getMethod("bind", ServiceBinder.class);

            if (
                !Modifier.isStatic(bindMethod.getModifiers())
            ) throw new RuntimeException(
                String.format(
                    "Method %s appears to be a service binder method, but is an instance method, not a static method.",
                    toString(bindMethod)
                )
            );

            var binder = new ServiceBinderImpl(
                this,
                bindMethod,
                proxyFactory,
                defaultMarkers,
                modulePreventsServiceDecoration
            );

            bindMethod.invoke(null, binder);

            binder.finish();

            remainingMethods.remove(bindMethod);

            return;
        } catch (NoSuchMethodException ex) {
            // No problem! Many modules will not have such a method.

            return;
        } catch (IllegalArgumentException ex) {
            failure = ex;
        } catch (IllegalAccessException ex) {
            failure = ex;
        } catch (InvocationTargetException ex) {
            failure = ex.getTargetException();
        }

        var methodId = toString(bindMethod);

        throw new RuntimeException(
            String.format(
                "Error invoking service binder method %s: %s",
                methodId,
                failure != null ? failure.getMessage() : "null"
            ),
            failure
        );
    }

    @Override
    public Set<AdvisorDefinition> getAdvisorDefs() {
        return toSet(advisorDefs);
    }

    private <K, V> Set<V> toSet(Map<K, V> map) {
        return new HashSet<>(map.values());
    }

    @Override
    public Set<StartupDef> getStartups() {
        return startups;
    }
}
