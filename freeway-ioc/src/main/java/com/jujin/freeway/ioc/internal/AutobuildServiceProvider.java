package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.AnnotationProvider;
import com.jujin.freeway.ioc.ServiceLocator;
import com.jujin.freeway.ioc.ServiceProvider;
import com.jujin.freeway.ioc.annotations.Autobuild;

/**
 * Checks for the {@link com.jujin.freeway.ioc.annotations.Autobuild} annotation
 * and, if so invokes
 * {@link com.jujin.freeway.ioc.ServiceLocator#autobuild(Class)} on it.
 */
public class AutobuildServiceProvider implements ServiceProvider {

    @Override
    public <T> T resolve(
        Class<T> objectType,
        AnnotationProvider annotationProvider,
        ServiceLocator locator) {
        Autobuild annotation = annotationProvider.getAnnotation(
            Autobuild.class);

        if (annotation != null)
            return locator.autobuild(
                "Autobuilding instance of " + objectType.getName(),
                objectType);

        return null;
    }
}
