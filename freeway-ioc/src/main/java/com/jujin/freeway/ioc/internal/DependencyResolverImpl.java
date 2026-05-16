package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.*;
import com.jujin.freeway.ioc.advisor.OperationTracker;
import com.jujin.freeway.ioc.annotations.PreventServiceDecoration;
import com.jujin.freeway.ioc.internal.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@PreventServiceDecoration
public class DependencyResolverImpl implements DependencyResolver {

    private final List<DependencyPolicy> configuration;

    private final OperationTracker tracker;

    public DependencyResolverImpl(
        List<DependencyPolicy> configuration,
        OperationTracker tracker
    ) {
        this.configuration = new ArrayList<>(
            Objects.requireNonNull(configuration, "configuration")
        );
        this.tracker = Objects.requireNonNull(tracker, "tracker");

        // Add this special case to the front of the list.
        this.configuration.add(
            0,
            new BuiltinStaticProvider<OperationTracker>(
                OperationTracker.class,
                tracker
            )
        );
    }

    @Override
    public <T> T resolve(
        final Class<T> objectType,
        final AnnotationProvider annotationProvider,
        final ServiceLocator locator,
        final boolean required
    ) {
        return tracker.invoke(
            String.format(
                "Resolving object of type %s using DependencyResolver",
                StringUtils.toSimpleTypeName(objectType)
            ),
            () -> {
                for (DependencyPolicy resolver : configuration) {
                    T result = resolver.resolve(
                        objectType,
                        annotationProvider,
                        locator
                    );

                    if (result != null) return result;
                }

                // If required, then we must obtain it the hard way, by
                // seeing if there's a single service that implements the interface.

                if (required) return locator.getService(objectType);

                return null;
            }
        );
    }
}
