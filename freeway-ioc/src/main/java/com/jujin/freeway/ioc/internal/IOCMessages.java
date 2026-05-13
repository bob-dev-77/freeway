package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ServiceDefinition;
import static com.jujin.freeway.ioc.internal.util.InternalUtils.asString;
import static com.jujin.freeway.ioc.internal.util.InternalUtils.toMessage;

import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.config.ContributionDef;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.internal.util.InternalUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

final class IOCMessages {

    static String buildMethodConflict(
        String serviceId,
        String conflict,
        String existing) {
        return String.format(
            "Service %s (defined by %s) conflicts with previously defined service defined by %s.",
            serviceId,
            conflict,
            existing);
    }

    static String serviceWrongInterface(
        String serviceId,
        Class<?> actualInterface,
        Class<?> requestedInterface) {
        return String.format(
            "Service '%s' implements interface %s, which is not compatible with the requested type %s.",
            serviceId,
            actualInterface.getName(),
            requestedInterface.getName());
    }

    static String instantiateBuilderError(
        Class<?> builderClass,
        Throwable cause) {
        return String.format(
            "Unable to instantiate class %s as a module: %s",
            builderClass.getName(),
            toMessage(cause));
    }

    static String noServiceMatchesType(Class<?> serviceInterface) {
        return String.format(
            "No service implements the interface %s.",
            serviceInterface.getName());
    }

    static String manyServiceMatches(
        Class<?> serviceInterface,
        List<String> ids) {
        StringBuilder buffer = new StringBuilder();

        for (int i = 0; i < ids.size(); i++) {
            if (i > 0)
                buffer.append(", ");

            buffer.append(ids.get(i));
        }

        return String.format(
            "Service interface %s is matched by %d services: %s. Automatic dependency resolution requires that exactly one service implement the interface.",
            serviceInterface.getName(),
            ids.size(),
            buffer.toString());
    }

    static String unknownScope(String name) {
        return String.format("Unknown service scope '%s'.", name);
    }

    static String recursiveServiceBuild(ServiceDefinition def) {
        return String.format(
            "Construction of service '%s' has failed due to recursion: the service depends on itself in some way. Please check %s for references to another service that is itself dependent on service '%1$s'.",
            def.getServiceId(),
            def.toString());
    }

    static String contributionWrongReturnType(Method method) {
        return String.format(
            "Method %s is named like a service contributor method, but the return type (%s) is not appropriate (it should be void). The return value will be ignored.",
            asString(method),
            InternalUtils.toSimpleTypeName(method.getReturnType()));
    }

    static String tooManyContributionParameters(Method method) {
        return String.format(
            "Service contribution method %s contains more than one parameter of type Configuration, OrderedConfiguration, or MappedConfiguration. Exactly one such parameter is required for a service contribution method.",
            asString(method));
    }

    static String noContributionParameter(Method method) {
        return String.format(
            "Service contribution method %s does not contain a parameter of type Configuration, OrderedConfiguration or MappedConfiguration. This parameter is how the method make contributions into the service's configuration.",
            asString(method));
    }

    static String contributionMethodError(Method method, Throwable cause) {
        return String.format(
            "Error invoking service contribution method %s: %s",
            asString(method),
            toMessage(cause));
    }

    static String contributionWasNull(String serviceId) {
        return String.format(
            "Service contribution (to service '%s') was null.",
            serviceId);
    }

    static String contributionKeyWasNull(String serviceId) {
        return String.format(
            "Key for service contribution (to service '%s') was null.",
            serviceId);
    }

    static String contributionWrongKeyType(
        String serviceId,
        Class<?> actualClass,
        Class<?> expectedClass) {
        return String.format(
            "Key for service contribution (to service '%s') was an instance of %s, but the expected key type was %s.",
            serviceId,
            actualClass.getName(),
            expectedClass.getName());
    }

    static String tooManyConfigurationParameters(String methodId) {
        return String.format(
            "Service builder method %s contains more than one parameter of type Collection, List, or Map. Parameters of this type are the way in which service configuration values, collected from service contributor methods, are provided to the service builder. Services are only allowed a single configuration",
            methodId);
    }

    static String genericTypeNotSupported(Type type) {
        return String.format(
            "Generic type '%s' is not supported. Only simple parameterized lists are supported.",
            type);
    }

    static String contributionDuplicateKey(
        String serviceId,
        Object key,
        ContributionDef existingDef) {
        return String.format(
            "Service contribution (to service '%s') for key '%s' conflicts with existing contribution (by %s).",
            serviceId,
            key,
            existingDef);
    }

    static String errorBuildingService(
        String serviceId,
        ServiceDefinition serviceDef,
        Throwable cause) {
        return String.format(
            "Error building service proxy for service '%s' (at %s): %s",
            serviceId,
            serviceDef,
            toMessage(cause));
    }

    static String noPublicConstructors(Class<?> moduleClass) {
        return String.format(
            "Module class %s does not contain any public constructors.",
            moduleClass.getName());
    }

    static String tooManyPublicConstructors(
        Class<?> moduleClass,
        Constructor<?> constructor) {
        return String.format(
            "Module class %s contains more than one public constructor. The first constructor, %s, is being used. You should change the class to have only a single public constructor.",
            moduleClass.getName(),
            constructor);
    }

    static String recursiveModuleConstructor(
        Class<?> builderClass,
        Constructor<?> constructor) {
        return String.format(
            "The constructor for module class %s is recursive: it depends on itself in some way. The constructor, %s, is in some way is triggering a service builder, advisor or contribution method within the class.",
            builderClass.getName(),
            constructor);
    }

    static String constructedConfiguration(Collection<?> result) {
        return String.format("Constructed configuration: %s", result);
    }

    static String constructedConfiguration(Map<?, ?> result) {
        return String.format("Constructed configuration: %s", result);
    }

    static String serviceConstructionFailed(
        ServiceDefinition serviceDef,
        Throwable cause) {
        return String.format(
            "Construction of service %s failed: %s",
            serviceDef.getServiceId(),
            toMessage(cause));
    }

    static String serviceIdConflict(
        String serviceId,
        ServiceDefinition existing,
        ServiceDefinition conflicting) {
        return String.format(
            "Service id '%s' has already been defined by %s and may not be redefined by %s. You should rename one of the service builder methods.",
            serviceId,
            existing,
            conflicting);
    }

    static String noConstructor(
        Class<?> implementationClass,
        String serviceId) {
        return String.format(
            "Class %s (implementation of service '%s') does not contain any public constructors.",
            implementationClass.getName(),
            serviceId);
    }

    static String abstractServiceImplementation(
        Class<?> implementationClass,
        String serviceId) {
        return String.format(
            "Class %s (implementation of service '%s') is abstract.",
            implementationClass.getName(),
            serviceId);
    }

    static String bindMethodMustBeStatic(String methodId) {
        return String.format(
            "Method %s appears to be a service binder method, but is an instance method, not a static method.",
            methodId);
    }

    static String errorInBindMethod(String methodId, Throwable cause) {
        return String.format(
            "Error invoking service binder method %s: %s",
            methodId,
            toMessage(cause));
    }

    static String noAutobuildConstructor(Class<?> clazz) {
        return String.format(
            "Class %s does not contain a public constructor needed to autobuild.",
            clazz.getName());
    }

    private static String toJavaClassNames(List<Class<?>> classes) {
        Class<?>[] asArray = classes.toArray(new Class<?>[classes.size()]);
        String[] namesArray = InternalUtils.toSimpleTypeNames(asArray);
        List<String> names = new ArrayList<>(Arrays.asList(namesArray));

        return InternalUtils.joinSorted(names);
    }

    static String noServicesMatchMarker(
        Class<?> objectType,
        List<Class<?>> markers) {
        return String.format(
            "Unable to locate any service assignable to type %s with marker annotation(s) %s.",
            InternalUtils.toSimpleTypeName(objectType),
            toJavaClassNames(markers));
    }

    static String manyServicesMatchMarker(
        Class<?> objectType,
        List<Class<?>> markers,
        Collection<ServiceDefinition> matchingServices) {
        return String.format(
            "Unable to locate a single service assignable to type %s with marker annotation(s) %s. All of the following services match: %s.",
            InternalUtils.toSimpleTypeName(objectType),
            toJavaClassNames(markers),
            InternalUtils.joinSorted(matchingServices));
    }

    static String overlappingServiceProxyProviders() {
        return "Setting a new service proxy provider when there's already an existing provider. This may indicate that you have multiple IoC Registries.";
    }

    static String unexpectedServiceProxyProvider() {
        return "Unexpected service proxy provider when clearing the provider. This may indicate that you have multiple IoC Registries.";
    }

    static String noProxyProvider(String serviceId) {
        return String.format(
            "Service token for service '%s' can not be converted back into a proxy because no proxy provider has been registered. This may indicate that an IoC Registry has not been started yet.",
            serviceId);
    }

    static String contributionForNonexistentService(ContributionDef cd) {
        return String.format(
            "Contribution %s is for service '%s', which does not exist.",
            cd,
            cd.getServiceId());
    }

    static String contributionForUnqualifiedService(ContributionDef cd) {
        return String.format(
            "Contribution %s is for service '%s' qualified with marker annotations %s, which does not exist.",
            cd,
            cd.getServiceInterface(),
            cd.getMarkers());
    }
}
