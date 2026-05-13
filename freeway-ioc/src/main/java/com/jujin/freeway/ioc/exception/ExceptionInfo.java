package com.jujin.freeway.ioc.exception;

import java.util.List;

/**
 * Contains information about an analyzed exception.
 *
 * @see ExceptionAnalysis
 */
public interface ExceptionInfo {
    /**
     * The exception class name.
     */
    String getClassName();

    /**
     * The message associated with the exception, possibly null.
     */
    String getMessage();

    /**
     * Returns the names of the properties of the exception, sorted alphabetically.
     */
    List<String> getPropertyNames();

    /**
     * Returns a specific property of the exception by name.
     */
    Object getProperty(String name);

    /**
     * Returns the stack trace elements. Generally this is an empty list except for
     * the deepest exception.
     */
    List<StackTraceElement> getStackTrace();
}
