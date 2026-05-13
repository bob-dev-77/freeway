package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.exception.FreewayException;

/**
 * Exception used when trying to assemble a page but different versions of the
 * same class are found.
 */
public class DifferentClassVersionsException extends FreewayException {

    private static final long serialVersionUID = 1L;

    private final String className;

    private final ClassLoader classLoader1;

    private final ClassLoader classLoader2;

    public DifferentClassVersionsException(String message, String className, ClassLoader classLoader1,
        ClassLoader classLoader2) {
        super(message, null);
        this.className = className;
        this.classLoader1 = classLoader1;
        this.classLoader2 = classLoader2;
    }

    public String getClassName() {
        return className;
    }

    public ClassLoader getClassLoader1() {
        return classLoader1;
    }

    public ClassLoader getClassLoader2() {
        return classLoader2;
    }

}
