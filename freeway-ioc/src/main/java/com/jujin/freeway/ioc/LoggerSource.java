package com.jujin.freeway.ioc;

import org.slf4j.Logger;

/**
 * A wrapper around SLF4J's LoggerFactory that exists to allow particular
 * projects to "hook" the creation of Logger instances.
 */
public interface LoggerSource {
    /**
     * Creates or retrieves a log based on Class. This is rarely used in Freeway
     * IOC.
     */
    Logger getLogger(Class<?> clazz);

    /**
     * Creates or retrieves a log based on name. Typically, the name will be a
     * service id.
     */
    Logger getLogger(String name);
}
