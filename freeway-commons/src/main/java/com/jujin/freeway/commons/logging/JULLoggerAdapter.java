package com.jujin.freeway.commons.logging;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.slf4j.Marker;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.helpers.MessageFormatter;

/**
 * An SLF4J {@link org.slf4j.Logger} that delegates to
 * {@link java.util.logging.Logger}.
 */
public class JULLoggerAdapter extends LegacyAbstractLogger {

    private static final String FQCN = JULLoggerAdapter.class.getName();

    private final Logger julLogger;

    JULLoggerAdapter(Logger julLogger) {
        this.julLogger = julLogger;
        this.name = julLogger.getName();
    }

    @Override
    protected String getFullyQualifiedCallerName() {
        return name;
    }

    @Override
    protected void handleNormalizedLoggingCall(
        org.slf4j.event.Level level,
        Marker marker,
        String msg,
        Object[] args,
        Throwable throwable) {

        Level julLevel = toJULLevel(level);
        if (!julLogger.isLoggable(julLevel)) {
            return;
        }

        String formatted = MessageFormatter.basicArrayFormat(msg, args);

        var record = new LogRecord(julLevel, formatted);
        record.setLoggerName(julLogger.getName());
        if (throwable != null) {
            record.setThrown(throwable);
        }

        inferCaller(record);

        julLogger.log(record);
    }

    @Override
    public boolean isTraceEnabled() {
        return julLogger.isLoggable(Level.FINEST);
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return julLogger.isLoggable(Level.FINEST);
    }

    @Override
    public boolean isDebugEnabled() {
        return julLogger.isLoggable(Level.FINE);
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return julLogger.isLoggable(Level.FINE);
    }

    @Override
    public boolean isInfoEnabled() {
        return julLogger.isLoggable(Level.INFO);
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return julLogger.isLoggable(Level.INFO);
    }

    @Override
    public boolean isWarnEnabled() {
        return julLogger.isLoggable(Level.WARNING);
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return julLogger.isLoggable(Level.WARNING);
    }

    @Override
    public boolean isErrorEnabled() {
        return julLogger.isLoggable(Level.SEVERE);
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return julLogger.isLoggable(Level.SEVERE);
    }

    static Level toJULLevel(org.slf4j.event.Level slf4jLevel) {
        switch (slf4jLevel) {
            case TRACE:
                return Level.FINEST;
            case DEBUG:
                return Level.FINE;
            case INFO:
                return Level.INFO;
            case WARN:
                return Level.WARNING;
            case ERROR:
                return Level.SEVERE;
            default:
                return Level.INFO;
        }
    }

    private void inferCaller(LogRecord record) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();

        boolean foundFqcn = false;
        for (StackTraceElement frame : stack) {
            String className = frame.getClassName();
            if (className.equals(FQCN)) {
                foundFqcn = true;
            } else if (foundFqcn
                && !className.startsWith("org.slf4j.")
                && !className.startsWith("java.util.logging.")
                && !className.startsWith("java.lang.reflect.")
                && !className.startsWith("sun.reflect.")) {
                record.setSourceClassName(className);
                record.setSourceMethodName(frame.getMethodName());
                return;
            }
        }
    }
}
