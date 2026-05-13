package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ServiceLocator;
import com.jujin.freeway.ioc.coercion.TypeCoercer;

/**
 * A proxy for the {@link TypeCoercer}
 *
 */
public final class TypeCoercerProxyImpl implements TypeCoercerProxy {
    private final ServiceLocator locator;

    private TypeCoercer delegate;

    public TypeCoercerProxyImpl(ServiceLocator locator) {
        this.locator = locator;
    }

    private TypeCoercer delegate() {
        if (delegate == null)
            delegate = locator.getService(TypeCoercer.class);

        return delegate;
    }

    @Override
    public <S, T> T coerce(S input, Class<T> targetType) {
        if (targetType.isInstance(input))
            return targetType.cast(input);

        return delegate().coerce(input, targetType);
    }

}
