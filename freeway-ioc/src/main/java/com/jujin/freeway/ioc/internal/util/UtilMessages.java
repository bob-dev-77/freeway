package com.jujin.freeway.ioc.internal.util;

class UtilMessages {

    private UtilMessages() {}

    static String dependencyCycle(
        DependencyNode<?> dependency,
        DependencyNode<?> node) {
        return String.format(
            "Unable to add '%s' as a dependency of '%s', as that forms a dependency cycle ('%<s' depends on itself via '%1$s'). The dependency has been ignored.",
            dependency.getId(),
            node.getId());
    }

    static String duplicateOrderer(String id) {
        return String.format(
            "Could not add object with duplicate id '%s'. The duplicate object has been ignored.",
            id);
    }

    static String constraintFormat(String constraint, String id) {
        return String.format(
            "Could not parse ordering constraint '%s' (for '%s'). The constraint has been ignored.",
            constraint,
            id);
    }

    static String oneShotLock(StackTraceElement element) {
        return String.format("Method %s may no longer be invoked.", element);
    }

    static String badMarkerAnnotation(Class<?> annotationClass) {
        return String.format(
            "Marker annotation class %s is not valid because it is not visible at runtime. Add a @Retention(RetentionPolicy.RUNTIME) to the class.",
            annotationClass.getName());
    }
}
