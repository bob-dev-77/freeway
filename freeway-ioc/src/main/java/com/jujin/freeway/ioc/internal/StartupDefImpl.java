package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ModuleInstanceSource;
import com.jujin.freeway.ioc.ServiceLocator;
import com.jujin.freeway.ioc.advisor.OperationTracker;
import com.jujin.freeway.ioc.internal.util.DisplayUtils;
import com.jujin.freeway.ioc.internal.util.InjectionContext;
import com.jujin.freeway.ioc.internal.util.InjectionPlanner;
import com.jujin.freeway.ioc.internal.util.MappedInjectionContext;
import com.jujin.freeway.ioc.internal.util.ReflectionSupport;
import com.jujin.freeway.ioc.lifecycle.ObjectCreator;
import com.jujin.freeway.ioc.lifecycle.StartupDef;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;

public class StartupDefImpl implements StartupDef {

    private final Method startupMethod;

    public StartupDefImpl(Method contributorMethod) {
        this.startupMethod = contributorMethod;
    }

    @Override
    public void invoke(
        final ModuleInstanceSource moduleBuilderSource,
        final OperationTracker tracker,
        final ServiceLocator locator,
        final Logger logger
    ) {
        tracker.run(
            String.format(
                "Invoking startup method %s.",
                DisplayUtils.asString(startupMethod)
            ),
            new Runnable() {
                @Override
                public void run() {
                    Map<Class<?>, Object> resourceMap = new HashMap<>();

                    resourceMap.put(ServiceLocator.class, locator);
                    resourceMap.put(Logger.class, logger);

                    InjectionContext injectionContext =
                        new MappedInjectionContext(resourceMap);

                    Throwable fail = null;

                    Object moduleInstance = ReflectionSupport.isStatic(
                        startupMethod
                    )
                        ? null
                        : moduleBuilderSource.getInstance();

                    try {
                        ObjectCreator<?>[] parameters =
                            InjectionPlanner.resolveMethodParameters(
                                startupMethod,
                                locator,
                                injectionContext,
                                tracker
                            );

                        startupMethod.invoke(
                            moduleInstance,
                            InjectionPlanner.realizeAll(parameters)
                        );
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
            }
        );
    }
}
