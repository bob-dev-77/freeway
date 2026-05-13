package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.config.ContributionDef;

import java.util.Map;

public class MappedConfigurationOverride<K, V> {

    private final Map<K, V> configuration;

    private final K key;

    private final V overrideValue;

    private final ContributionDef contribDef;

    public MappedConfigurationOverride(
        ContributionDef contribDef,
        Map<K, V> configuration,
        K key,
        V overrideValue) {
        this.contribDef = contribDef;
        this.configuration = configuration;
        this.key = key;
        this.overrideValue = overrideValue;
    }

    void apply() {
        if (!configuration.containsKey(key))
            throw new IllegalArgumentException(
                String.format(
                    "Override for key %s (at %s) does not match an existing key.",
                    key,
                    contribDef));

        if (overrideValue == null)
            configuration.remove(key);
        else
            configuration.put(key, overrideValue);
    }

    public ContributionDef getContribDef() {
        return contribDef;
    }
}
