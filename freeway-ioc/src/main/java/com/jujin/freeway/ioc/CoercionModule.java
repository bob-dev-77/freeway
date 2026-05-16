package com.jujin.freeway.ioc;

import com.jujin.freeway.ioc.annotations.Builtin;
import com.jujin.freeway.ioc.annotations.Contribute;
import com.jujin.freeway.ioc.annotations.Marker;
import com.jujin.freeway.ioc.coercion.CoercionTuple;
import com.jujin.freeway.ioc.coercion.TypeCoercer;
import com.jujin.freeway.ioc.config.MappedConfiguration;
import com.jujin.freeway.ioc.internal.BasicTypeCoercions;

@Marker(Builtin.class)
public class CoercionModule {

    @Contribute(TypeCoercer.class)
    public static void provideBasicTypeCoercions(
        MappedConfiguration<CoercionTuple.Key, CoercionTuple> configuration
    ) {
        BasicTypeCoercions.provideBasicTypeCoercions(configuration);
    }

    @Contribute(TypeCoercer.class)
    public static void provideJSR310TypeCoercions(
        MappedConfiguration<CoercionTuple.Key, CoercionTuple> configuration
    ) {
        BasicTypeCoercions.provideJSR310TypeCoercions(configuration);
    }
}
