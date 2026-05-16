package com.jujin.freeway.ioc.internal.util;

import com.jujin.freeway.ioc.AnnotationProvider;
import com.jujin.freeway.ioc.ServiceLocator;
import com.jujin.freeway.ioc.advisor.OperationTracker;
import com.jujin.freeway.ioc.annotations.PostInjection;
import com.jujin.freeway.ioc.internal.util.InternalUtils;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.function.Supplier;
import javax.annotation.PostConstruct;
import javax.inject.Named;
import org.slf4j.Logger;

/**
 * Builds instance plans that encapsulate the full lifecycle of constructing
 * an object: constructor invocation, field injection, and post-injection
 * method callbacks ({@code @PostInjection} / {@code @PostConstruct}).
 */
public class InstancePlanBuilder {

    /**
     * Creates a complete plan to instantiate a class via its constructor,
     * then inject fields and invoke post-injection methods.
     */
    public static <T> ObjectCreator<T> build(
        final OperationTracker tracker,
        final ServiceLocator locator,
        final InjectionContext resources,
        final Logger logger,
        final String description,
        final Constructor<T> constructor
    ) {
        return tracker.invoke(
            String.format(
                "Creating plan to instantiate %s via %s",
                constructor.getDeclaringClass().getName(),
                constructor
            ),
            () -> {
                InternalUtils.validateConstructorForAutobuild(constructor);

                ObjectCreator[] constructorParameters =
                    InjectionPlanner.resolveParameters(
                        locator,
                        resources,
                        constructor.getParameterTypes(),
                        constructor.getGenericParameterTypes(),
                        constructor.getParameterAnnotations(),
                        tracker
                    );

                var core = (Supplier<T>) () ->
                    InjectionPlanner.invokeConstructor(
                        MethodHandleUtils.constructorHandle(constructor),
                        constructorParameters
                    );

                var wrapped =
                    logger == null
                        ? core
                        : new LoggingInvokableWrapper<T>(
                              logger,
                              description,
                              core
                          );

                InstancePlan<T> plan = new InstancePlan(
                    tracker,
                    description,
                    wrapped
                );

                includeFieldInjections(
                    plan,
                    tracker,
                    locator,
                    resources,
                    constructor.getDeclaringClass()
                );

                includePostInjectionMethods(
                    plan,
                    tracker,
                    locator,
                    resources,
                    constructor.getDeclaringClass()
                );

                return plan;
            }
        );
    }

    /**
     * Creates a plan to invoke a method (service builder method) with resolved parameters.
     */
    public static <T> ObjectCreator<T> buildForMethod(
        final OperationTracker tracker,
        final ServiceLocator locator,
        final InjectionContext resources,
        final Logger logger,
        final String description,
        final Object instance,
        final Method method
    ) {
        return tracker.invoke("Creating plan to invoke " + method, () -> {
            ObjectCreator[] methodParameters =
                InjectionPlanner.resolveMethodParameters(
                    method,
                    locator,
                    resources,
                    tracker
                );

            var core = new MethodHandleInvoker<T>(
                instance,
                method,
                methodParameters
            );

            var wrapped =
                logger == null
                    ? core
                    : new LoggingInvokableWrapper<T>(logger, description, core);

            return new InstancePlan(tracker, description, wrapped);
        });
    }

    // ── Field injection support ──────────────────────────────────────

    private static <T> void includeFieldInjections(
        final InstancePlan<T> plan,
        OperationTracker tracker,
        final ServiceLocator locator,
        final InjectionContext resources,
        Class<T> instantiatedClass
    ) {
        Class clazz = instantiatedClass;
        while (clazz != Object.class) {
            Field[] fields = clazz.getDeclaredFields();
            for (final Field f : fields) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isFinal(mod)) continue;

                final var ap = new AnnotationProvider() {
                    @Override
                    public <T extends Annotation> T getAnnotation(
                        Class<T> annotationClass
                    ) {
                        return f.getAnnotation(annotationClass);
                    }
                };

                var desc = String.format(
                    "Calculating possible injection value for field %s.%s (%s)",
                    clazz.getName(),
                    f.getName(),
                    InternalUtils.toSimpleTypeName(f.getType())
                );

                tracker.run(desc, () -> {
                    final Class<?> fieldType = f.getType();

                    com.jujin.freeway.ioc.annotations.Inject fwInject =
                        ap.getAnnotation(
                            com.jujin.freeway.ioc.annotations.Inject.class
                        );

                    if (
                        ap.getAnnotation(javax.inject.Inject.class) != null ||
                        fwInject != null
                    ) {
                        if (fwInject != null) {
                            String sid = fwInject.value();
                            if (sid != null && !sid.isEmpty()) {
                                addInjectPlan(
                                    plan,
                                    f,
                                    locator.getService(sid, fieldType)
                                );
                                return;
                            }
                        }
                        Named named = ap.getAnnotation(Named.class);
                        if (named == null) {
                            addInjectPlan(
                                plan,
                                f,
                                locator.getObject(fieldType, ap)
                            );
                        } else {
                            addInjectPlan(
                                plan,
                                f,
                                locator.getService(named.value(), fieldType)
                            );
                        }
                    }
                });
            }
            clazz = clazz.getSuperclass();
        }
    }

    private static <T> void addInjectPlan(
        final InstancePlan<T> plan,
        final Field field,
        final Object injectedValue
    ) {
        plan.add(
            new InitializePlan<T>() {
                @Override
                public String description() {
                    return String.format(
                        "Injecting value into field %s (%s)",
                        field.getName(),
                        InternalUtils.toSimpleTypeName(field.getType())
                    );
                }

                @Override
                public void initialize(T instance) {
                    try {
                        MethodHandleUtils.varHandle(field).set(
                            instance,
                            injectedValue
                        );
                    } catch (Exception ex) {
                        throw new RuntimeException(
                            String.format(
                                "Unable to set field '%s' of %s to %s: %s",
                                field.getName(),
                                instance,
                                injectedValue,
                                InternalUtils.toMessage(ex)
                            )
                        );
                    }
                }
            }
        );
    }

    // ── Post-injection support ──────────────────────────────────────

    private static <T> void includePostInjectionMethods(
        final InstancePlan<T> plan,
        final OperationTracker tracker,
        final ServiceLocator locator,
        final InjectionContext resources,
        Class<T> instantiatedClass
    ) {
        for (Method m : instantiatedClass.getMethods()) {
            if (
                hasAnnotation(m, PostInjection.class) ||
                hasAnnotation(m, PostConstruct.class)
            ) {
                includePostInjectionMethod(
                    plan,
                    tracker,
                    locator,
                    resources,
                    m
                );
            }
        }
    }

    private static void includePostInjectionMethod(
        final InstancePlan<?> plan,
        final OperationTracker tracker,
        final ServiceLocator locator,
        final InjectionContext resources,
        final Method method
    ) {
        tracker.run(
            "Computing parameters for post-injection method " + method,
            () -> {
                final ObjectCreator[] parameters =
                    InjectionPlanner.resolveMethodParameters(
                        method,
                        locator,
                        resources,
                        tracker
                    );

                plan.add(
                    new InitializePlan<Object>() {
                        @Override
                        public String description() {
                            return "Invoking " + method;
                        }

                        private final MethodHandle mh =
                            MethodHandleUtils.methodHandle(method);

                        @Override
                        public void initialize(Object instance) {
                            Object[] realized = InjectionPlanner.realizeAll(
                                parameters
                            );
                            Object[] args = new Object[realized.length + 1];
                            args[0] = instance;
                            System.arraycopy(
                                realized,
                                0,
                                args,
                                1,
                                realized.length
                            );
                            try {
                                mh.invokeWithArguments(args);
                            } catch (RuntimeException | Error e) {
                                throw e;
                            } catch (Throwable t) {
                                throw new RuntimeException(
                                    String.format(
                                        "Exception invoking method %s: %s",
                                        method,
                                        InternalUtils.toMessage(t)
                                    ),
                                    t
                                );
                            }
                        }
                    }
                );
            }
        );
    }

    private static boolean hasAnnotation(
        AccessibleObject obj,
        Class<? extends Annotation> annotationClass
    ) {
        return obj.getAnnotation(annotationClass) != null;
    }

    private InstancePlanBuilder() {}
}
