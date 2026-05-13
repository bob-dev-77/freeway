package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.config.Configuration;
import com.jujin.freeway.ioc.ServiceLocator;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.internal.util.InternalUtils;
import java.util.Collection;

/**
 * Wraps a {@link java.util.Collection} as a
 * {@link com.jujin.freeway.ioc.Configuration} and perform validation that
 * collected value are of the correct type.
 */
public class ValidatingConfigurationWrapper<T> implements Configuration<T> {

    private final TypeCoercerProxy typeCoercer;

    private final String serviceId;

    private final Class<T> expectedType;

    private final Collection<T> collection;

    private final Class<T> contributionType;

    private final ServiceLocator locator;

    public ValidatingConfigurationWrapper(
        Class<T> expectedType,
        ServiceLocator locator,
        TypeCoercerProxy typeCoercer,
        Collection<T> collection,
        String serviceId) {
        this.contributionType = expectedType;
        this.locator = locator;
        this.typeCoercer = typeCoercer;

        this.collection = collection;
        this.serviceId = serviceId;
        this.expectedType = expectedType;
    }

    @Override
    public void add(T object) {
        if (object == null)
            throw new NullPointerException(
                IOCMessages.contributionWasNull(serviceId));

        T coerced = typeCoercer.coerce(object, expectedType);

        collection.add(coerced);
    }

    @Override
    public void addInstance(Class<? extends T> clazz) {
        add(InternalUtils.instantiate(contributionType, locator, clazz));
    }
}
