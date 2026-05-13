package com.jujin.freeway.ioc.coercion;

import com.jujin.freeway.ioc.exception.FreewayException;

/**
 * Exception used when a {@link Coercion} throws an exception while trying to
 * coerce a value.
 *
 */
public class CoercionFailedException extends FreewayException {

    private static final long serialVersionUID = 1L;

    public CoercionFailedException(String message, Throwable cause) {
        super(message, cause);
    }

}
