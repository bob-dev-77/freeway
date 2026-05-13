package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.AnnotationProvider;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Chain of command for {@link AnnotationProvider}.
 */
public class AnnotationProviderChain implements AnnotationProvider {
    private final AnnotationProvider[] providers;

    public AnnotationProviderChain(AnnotationProvider[] providers) {
        this.providers = providers;
    }

    /**
     * Creates an AnnotationProvider from the list of providers. Returns either an
     * {@link AnnotationProviderChain} or the sole element in the list.
     */
    public static AnnotationProvider create(List<AnnotationProvider> providers) {
        int size = providers.size();

        if (size == 1)
            return providers.get(0);

        return new AnnotationProviderChain(providers.toArray(new AnnotationProvider[providers.size()]));
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        for (AnnotationProvider p : providers) {
            T result = p.getAnnotation(annotationClass);

            if (result != null)
                return result;
        }

        return null;
    }
}
