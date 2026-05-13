package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.config.ContributionDef;
import com.jujin.freeway.ioc.internal.util.InternalUtils;
import com.jujin.freeway.ioc.internal.util.Orderer;

class OrderedConfigurationOverride<T> {

    private final Orderer<T> orderer;

    private final String id;

    private final T replacementObject;

    private final String[] constraints;

    private final ContributionDef contribDef;

    OrderedConfigurationOverride(
        Orderer<T> orderer,
        String id,
        T replacementObject,
        String[] constraints,
        ContributionDef contribDef) {
        this.orderer = orderer;
        this.id = id;
        this.replacementObject = replacementObject;
        this.constraints = constraints;
        this.contribDef = contribDef;
    }

    void apply() {
        try {
            orderer.override(id, replacementObject, constraints);
        } catch (Exception ex) {
            String message = String.format(
                "Failure processing override from %s: %s",
                contribDef,
                InternalUtils.toMessage(ex));

            throw new RuntimeException(message, ex);
        }
    }

    public ContributionDef getContribDef() {
        return contribDef;
    }
}
