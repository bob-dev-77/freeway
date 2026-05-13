package com.jujin.freeway.ioc.exception;

import java.util.List;

/**
 * Exception thrown when a value (typically from a map) is referenced that does
 * not exist, carrying the list of available values to aid debugging.
 */
public class UnknownValueException extends FreewayException {

    private final List<String> availableValues;

    public UnknownValueException(String message, List<String> availableValues) {
        super(message, null);
        this.availableValues = availableValues;
    }

    public UnknownValueException(
        String message,
        Throwable cause,
        List<String> availableValues) {
        super(message, cause);
        this.availableValues = availableValues;
    }

    public List<String> getAvailableValues() {
        return availableValues;
    }
}
