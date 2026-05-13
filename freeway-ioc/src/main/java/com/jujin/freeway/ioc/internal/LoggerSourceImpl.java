package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.LoggerSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple wrapper around SLF4J's LoggerFactory. The concept here is that Log
 * implementations could be provided that promote warnings or errors upto thrown
 * exceptions, for people who like their IOC container extra finicky. In
 * addition, the extra layer makes things a lot easier to mock.
 */
public class LoggerSourceImpl implements LoggerSource {

    @Override
    public Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }

    @Override
    public Logger getLogger(String name) {
        return LoggerFactory.getLogger(name);
    }
}
