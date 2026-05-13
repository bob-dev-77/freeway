package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ModuleBuilderSource;
import com.jujin.freeway.ioc.ServiceLocator;
import com.jujin.freeway.ioc.advisor.OperationTracker;
import com.jujin.freeway.ioc.internal.util.InjectionResources;
import com.jujin.freeway.ioc.internal.util.InternalUtils;
import com.jujin.freeway.ioc.internal.util.MapInjectionResources;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import com.jujin.freeway.ioc.lifecycle.StartupDef;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class StartupDefImpl implements StartupDef {

    private final Method startupMethod;

    public StartupDefImpl(Method contributorMethod) {
        this.startupMethod = contributorMethod;
    }

    @Override
    public void invoke(
        final ModuleBuilderSource moduleBuilderSource,
        final OperationTracker tracker,
        final ServiceLocator locator,
        final Logger logger) {
        tracker.run(
            String.format(
                "Invoking startup method %s.",
                InternalUtils.asString(startupMethod)),
            new Runnable() {
                @Override
                public void run() {
                    Map<Class<?>, Object> resourceMap = new HashMap<>();

                    resourceMap.put(ServiceLocator.class, locator);
                    resourceMap.put(Logger.class, logger);

                    InjectionResources injectionResources = new MapInjectionResources(resourceMap);

                    Throwable fail = null;

                    Object moduleInstance = InternalUtils.isStatic(
                        startupMethod)
                            ? null
                            : moduleBuilderSource.getModuleBuilder();

                    try {
                        ObjectCreator<?>[] parameters = InternalUtils.calculateParametersForMethod(
                            startupMethod,
                            locator,
                            injectionResources,
                            tracker);

                        startupMethod.invoke(
                            moduleInstance,
                            InternalUtils.realizeObjects(parameters));
                    } catch (InvocationTargetException ex) {
                        fail = ex.getTargetException();
                    } catch (RuntimeException ex) {
                        throw ex;
                    } catch (Exception ex) {
                        fail = ex;
                    }

                    if (fail != null) {
                        throw new RuntimeException(fail);
                    }
                }
            });
    }
}
