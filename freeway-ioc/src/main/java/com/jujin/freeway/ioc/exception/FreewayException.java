package com.jujin.freeway.ioc.exception;
import com.jujin.freeway.ioc.*;

/**
 * Exception class used as a replacement for {@link java.lang.RuntimeException}
 * when the exception is related to a particular location.
 */
public class FreewayException extends RuntimeException {
    /**
     * @param message
     *            a message (may be null)
     * @param cause
     *            if not null, the root cause of the exception
     */
    public FreewayException(String message, Throwable cause) {
        super(message, cause);
    }

}
