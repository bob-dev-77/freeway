package com.jujin.freeway.ioc.config;

/**
 * Constructs order constraints for {@link OrderedConfiguration}.
 *
 */
public final class OrderConstraintBuilder {
    /**
     * Adds an <i>after:id</i> constraint.
     */
    public static OrderConstraint after(String id) {
        return new OrderConstraint().after(id);
    }

    /**
     * Adds an <i>after:*</i> constraint.
     */
    public static OrderConstraint afterAll() {
        return new OrderConstraint().afterAll();
    }

    /**
     * Adds a <i>before:id</i> constraint.
     */
    public static OrderConstraint before(String id) {
        return new OrderConstraint().before(id);
    }

    /**
     * Adds a <i>before:*</i> constraint.
     */
    public static OrderConstraint beforeAll() {
        return new OrderConstraint().beforeAll();
    }
}
