package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.ServiceLocator;
import com.jujin.freeway.ioc.config.ContributionDef;
import com.jujin.freeway.ioc.config.OrderedConfiguration;
import com.jujin.freeway.ioc.internal.util.StringUtils;
import com.jujin.freeway.ioc.internal.util.Orderer;
import com.jujin.freeway.ioc.internal.util.ReflectionUtils;
import java.util.Map;

/**
 * Wraps a {@link java.util.List} as a
 * {@link com.jujin.freeway.ioc.OrderedConfiguration}, implementing validation
 * of values provided to an {@link com.jujin.freeway.ioc.OrderedConfiguration}.
 *
 * @param <T>
 */
public class ValidatingOrderedConfigurationWrapper<
    T
> implements OrderedConfiguration<T> {

    private final TypeCoercerProxy typeCoercer;

    private final Orderer<T> orderer;

    private final Class<T> expectedType;

    private final Map<String, OrderedConfigurationOverride<T>> overrides;

    private final ContributionDef contribDef;

    private final Class<T> contributionType;

    private final ServiceLocator locator;

    // Used to supply a default ordering constraint when none is supplied.
    private String priorId;

    public ValidatingOrderedConfigurationWrapper(
        Class<T> expectedType,
        ServiceLocator locator,
        TypeCoercerProxy typeCoercer,
        Orderer<T> orderer,
        Map<String, OrderedConfigurationOverride<T>> overrides,
        ContributionDef contribDef
    ) {
        this.contributionType = expectedType;
        this.locator = locator;
        this.typeCoercer = typeCoercer;

        this.orderer = orderer;
        this.overrides = overrides;
        this.contribDef = contribDef;
        this.expectedType = expectedType;
    }

    @Override
    public void add(String id, T object, String... constraints) {
        T coerced =
            object == null ? null : typeCoercer.coerce(object, expectedType);

        // https://issues.apache.org/jira/browse/ // Order each added contribution after
        // the previously added contribution
        // (in the same method) if no other constraint is supplied.
        if (constraints.length == 0 && priorId != null) {
            // Ugly: reassigning parameters is yuck.
            constraints = new String[] { "after:" + priorId };
        }

        orderer.add(id, coerced, constraints);

        priorId = id;
    }

    @Override
    public void override(String id, T object, String... constraints) {
        assert StringUtils.isNonBlank(id);

        T coerced =
            object == null ? null : typeCoercer.coerce(object, expectedType);

        OrderedConfigurationOverride<T> existing = overrides.get(id);

        if (existing != null) throw new IllegalArgumentException(
            String.format(
                "Contribution '%s' has already been overridden (by %s).",
                id,
                existing.getContribDef()
            )
        );

        overrides.put(
            id,
            new OrderedConfigurationOverride<T>(
                orderer,
                id,
                coerced,
                constraints,
                contribDef
            )
        );
    }

    @Override
    public void addInstance(
        String id,
        Class<? extends T> clazz,
        String... constraints
    ) {
        add(
            id,
            ReflectionUtils.instantiate(contributionType, locator, clazz),
            constraints
        );
    }

    @Override
    public void overrideInstance(
        String id,
        Class<? extends T> clazz,
        String... constraints
    ) {
        override(
            id,
            ReflectionUtils.instantiate(contributionType, locator, clazz),
            constraints
        );
    }
}
