package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.config.ContributionDef;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.config.MappedConfiguration;
import com.jujin.freeway.ioc.ServiceLocator;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.internal.util.InternalUtils;
import java.util.Map;

/**
 * A wrapper around a Map that provides the
 * {@link com.jujin.freeway.ioc.MappedConfiguration} interface, and provides two
 * forms of validation for mapped configurations:
 * <ul>
 * <li>If either key or value is null, then a warning is logged</li>
 * <li>If the key has previously been stored (by some other
 * {@link com.jujin.freeway.ioc.ContributionDef}, then a warning is logged</li>
 * </ul>
 * <p>
 * When a warning is logged, the key/value pair is not added to the delegate.
 * <p>
 * Handles instantiation of instances.
 *
 * @param <K>
 *            the key type
 * @param <V>
 *            the value type
 */
public class ValidatingMappedConfigurationWrapper<K, V> implements MappedConfiguration<K, V> {

    private final TypeCoercerProxy typeCoercer;

    private final Map<K, V> map;

    private final Map<K, MappedConfigurationOverride<K, V>> overrides;

    private final String serviceId;

    private final ContributionDef contributionDef;

    private final Class<K> expectedKeyType;

    private final Class<V> expectedValueType;

    private final Map<K, ContributionDef> keyToContributor;

    private final Class<V> contributionType;

    private final ServiceLocator locator;

    public ValidatingMappedConfigurationWrapper(
        Class<V> expectedValueType,
        ServiceLocator locator,
        TypeCoercerProxy typeCoercer,
        Map<K, V> map,
        Map<K, MappedConfigurationOverride<K, V>> overrides,
        String serviceId,
        ContributionDef contributionDef,
        Class<K> expectedKeyType,
        Map<K, ContributionDef> keyToContributor) {
        this.contributionType = expectedValueType;
        this.locator = locator;

        this.typeCoercer = typeCoercer;
        this.map = map;
        this.overrides = overrides;
        this.serviceId = serviceId;
        this.contributionDef = contributionDef;
        this.expectedKeyType = expectedKeyType;
        this.expectedValueType = expectedValueType;
        this.keyToContributor = keyToContributor;
    }

    @Override
    public void add(K key, V value) {
        validateKey(key);

        if (value == null)
            throw new NullPointerException(
                IOCMessages.contributionWasNull(serviceId));

        V coerced = typeCoercer.coerce(value, expectedValueType);

        ContributionDef existing = keyToContributor.get(key);

        if (existing != null)
            throw new IllegalArgumentException(
                IOCMessages.contributionDuplicateKey(serviceId, key, existing));

        map.put(key, coerced);

        // Remember that this key is provided by this contribution, when looking
        // for future conflicts.

        keyToContributor.put(key, contributionDef);
    }

    private void validateKey(K key) {
        if (key == null)
            throw new NullPointerException(
                IOCMessages.contributionKeyWasNull(serviceId));

        // Key types don't get coerced; not worth the effort, keys are almost always
        // String or Class
        // anyway.

        if (!expectedKeyType.isInstance(key))
            throw new IllegalArgumentException(
                IOCMessages.contributionWrongKeyType(
                    serviceId,
                    key.getClass(),
                    expectedKeyType));
    }

    @Override
    public void addInstance(K key, Class<? extends V> clazz) {
        add(key, InternalUtils.instantiate(contributionType, locator, clazz));
    }

    @Override
    public void override(K key, V value) {
        validateKey(key);

        V coerced = value == null ? null : typeCoercer.coerce(value, expectedValueType);

        MappedConfigurationOverride<K, V> existing = overrides.get(key);

        if (existing != null)
            throw new IllegalArgumentException(
                String.format(
                    "Contribution key %s has already been overridden (by %s).",
                    key,
                    existing.getContribDef()));

        overrides.put(
            key,
            new MappedConfigurationOverride<K, V>(
                contributionDef,
                map,
                key,
                coerced));
    }

    @Override
    public void overrideInstance(K key, Class<? extends V> clazz) {
        override(
            key,
            InternalUtils.instantiate(contributionType, locator, clazz));
    }
}
