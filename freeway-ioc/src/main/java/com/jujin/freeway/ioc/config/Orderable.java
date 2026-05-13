package com.jujin.freeway.ioc.config;

import com.jujin.freeway.ioc.internal.util.InternalUtils;

/**
 * A wrapper that allows objects of a target type to be ordered. Each Orderable
 * object is given a unique id and a set of pre-requisites (objects which should
 * be ordered earlier) and post-requisites (objects which should be ordered
 * later).
 *
 * @param <T>
 *            the wrapped type
 */
public class Orderable<T> {
    private final String id;

    private final T target;

    private final String[] constraints;

    /**
     * @param id
     *            unique identifier for the target object
     * @param target
     *            the object to be ordered; this may also be null (in which case the
     *            id represents a placeholder)
     */

    public Orderable(String id, T target, String... constraints) {
        assert InternalUtils.isNonBlank(id);
        this.id = id;
        this.target = target;
        this.constraints = constraints;
    }

    public String getId() {
        return id;
    }

    public T getTarget() {
        return target;
    }

    public String[] getConstraints() {
        return constraints;
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder("Orderable[");

        buffer.append(id);

        for (String c : constraints) {
            buffer.append(' ');
            buffer.append(c);
        }

        buffer.append(' ');
        buffer.append(target.toString());
        buffer.append(']');

        return buffer.toString();
    }
}
