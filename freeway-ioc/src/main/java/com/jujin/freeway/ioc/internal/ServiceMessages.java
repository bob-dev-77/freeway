package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.coercion.Coercion;

import static com.jujin.freeway.ioc.internal.util.InternalUtils.toMessage;

public class ServiceMessages {

    private ServiceMessages() {}

    public static String noSuchProperty(Class<?> clazz, String propertyName) {
        return String.format(
            "Class %s does not contain a property named '%s'.",
            clazz.getName(),
            propertyName);
    }

    public static String readFailure(
        String propertyName,
        Object instance,
        Throwable cause) {
        return String.format(
            "Error reading property '%s' of %s: %s",
            propertyName,
            instance,
            toMessage(cause));
    }

    public static String propertyTypeMismatch(
        String propertyName,
        Class<?> sourceClass,
        Class<?> propertyType,
        Class<?> expectedType) {
        return String.format(
            "Property '%s' of class %s is of type %s, which is not assignable to type %s.",
            propertyName,
            sourceClass.getName(),
            propertyType.getName(),
            expectedType.getName());
    }

    public static String shutdownListenerError(
        Object listener,
        Throwable cause) {
        return String.format(
            "Error notifying %s of registry shutdown: %s",
            listener,
            toMessage(cause));
    }

    public static String failedCoercion(
        Object input,
        Class<?> targetType,
        Coercion<?, ?> coercion,
        Throwable cause) {
        return String.format(
            "Coercion of %s to type %s (via %s) failed: %s",
            String.valueOf(input),
            targetType.getCanonicalName(),
            coercion,
            toMessage(cause));
    }

    public static String registryShutdown(String serviceId) {
        return String.format(
            "Proxy for service %s is no longer active because the IOC Registry has been shut down.",
            serviceId);
    }

    public static String serviceBuildFailure(
        String serviceId,
        Throwable cause) {
        return String.format(
            "Exception constructing service '%s': %s",
            serviceId,
            toMessage(cause));
    }
}
