package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.*;
import com.jujin.freeway.ioc.ObjectInjector;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.advisor.OperationTracker;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.annotations.PreventServiceDecoration;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.internal.util.InternalUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@PreventServiceDecoration
public class ObjectInjectorImpl implements ObjectInjector {

    private final List<ServiceProvider> configuration;

    private final OperationTracker tracker;

    public ObjectInjectorImpl(
        List<ServiceProvider> configuration,
        OperationTracker tracker) {
        this.configuration = new ArrayList<>(Objects.requireNonNull(configuration, "configuration"));
        this.tracker = Objects.requireNonNull(tracker, "tracker");

        // Add this special case to the front of the list.
        this.configuration.add(
            0,
            new StaticServiceProvider<OperationTracker>(
                OperationTracker.class,
                tracker));
    }

    @Override
    public <T> T inject(
        final Class<T> objectType,
        final AnnotationProvider annotationProvider,
        final ServiceLocator locator,
        final boolean required) {
        return tracker.invoke(
            String.format(
                "Resolving object of type %s using ObjectInjector",
                InternalUtils.toSimpleTypeName(objectType)),
            () -> {
                for (ServiceProvider resolver : configuration) {
                    T result = resolver.resolve(
                        objectType,
                        annotationProvider,
                        locator);

                    if (result != null)
                        return result;
                }

                // If required, then we must obtain it the hard way, by
                // seeing if there's a single service that implements the interface.

                if (required)
                    return locator.getService(objectType);

                return null;
            });
    }
}
