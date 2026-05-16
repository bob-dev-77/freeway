package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.AnnotationProvider;
import com.jujin.freeway.ioc.DependencyPolicy;
import com.jujin.freeway.ioc.ServiceLocator;
import com.jujin.freeway.ioc.annotations.Builtin;
import com.jujin.freeway.ioc.annotations.IntermediateType;
import com.jujin.freeway.ioc.annotations.Symbol;
import com.jujin.freeway.ioc.annotations.Value;
import com.jujin.freeway.ioc.coercion.TypeCoercer;
import com.jujin.freeway.ioc.symbol.SymbolSource;

/**
 * Resolves injection values from {@link Symbol} and {@link Value} annotations.
 * The string value is expanded via {@link SymbolSource}, optionally coerced
 * through an {@link IntermediateType}, then coerced to the target type.
 */
public class BuiltinConfigProvider implements DependencyPolicy {

    private final SymbolSource symbolSource;
    private final TypeCoercer typeCoercer;

    public BuiltinConfigProvider(
        @Builtin SymbolSource symbolSource,
        @Builtin TypeCoercer typeCoercer
    ) {
        this.symbolSource = symbolSource;
        this.typeCoercer = typeCoercer;
    }

    @Override
    public <T> T resolve(
        Class<T> objectType,
        AnnotationProvider annotationProvider,
        ServiceLocator locator
    ) {
        Symbol symbolAnno = annotationProvider.getAnnotation(Symbol.class);
        Value valueAnno = annotationProvider.getAnnotation(Value.class);
        if (symbolAnno == null && valueAnno == null) return null;

        Object raw =
            symbolAnno != null
                ? symbolSource.resolve(symbolAnno.value())
                : symbolSource.expand(valueAnno.value());

        IntermediateType intermediate = annotationProvider.getAnnotation(
            IntermediateType.class
        );
        Object coerced =
            intermediate != null
                ? typeCoercer.coerce(raw, intermediate.value())
                : raw;

        return typeCoercer.coerce(coerced, objectType);
    }
}
