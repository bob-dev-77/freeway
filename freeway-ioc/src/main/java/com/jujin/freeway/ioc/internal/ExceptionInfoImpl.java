package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.exception.ExceptionInfo;
import com.jujin.freeway.ioc.internal.util.InternalUtils;

import java.util.List;
import java.util.Map;

import static java.util.Collections.unmodifiableList;

public class ExceptionInfoImpl implements ExceptionInfo {
    private final String className;

    private final String message;

    private final Map<String, Object> properties;

    private final List<StackTraceElement> stackTrace;

    public ExceptionInfoImpl(Throwable t, Map<String, Object> properties, List<StackTraceElement> stackTrace) {
        className = t.getClass().getName();
        message = t.getMessage() != null ? t.getMessage() : "";

        this.properties = properties;
        this.stackTrace = unmodifiableList(stackTrace);
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public Object getProperty(String name) {
        return properties.get(name);
    }

    @Override
    public List<String> getPropertyNames() {
        return InternalUtils.sortedKeys(properties);
    }

    @Override
    public List<StackTraceElement> getStackTrace() {
        return stackTrace;
    }

}
