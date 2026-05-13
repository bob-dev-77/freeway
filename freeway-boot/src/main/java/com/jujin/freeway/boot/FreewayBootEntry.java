package com.jujin.freeway.boot;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as the primary source for a freeway-boot application.
 * <p>
 * When present on the class passed to
 * {@link FreewayApplication#run(Class, String[])}, freeway-boot will
 * automatically discover and load auto-configuration classes from the classpath
 * via the {@code META-INF/freeway/auto-configuration.properties} SPI.
 * <p>
 * Auto-configuration classes are regular IoC module classes that can use the
 * full suite of module conventions ({@code bind()}, {@code build*()},
 * {@code contribute*()}, {@code @Startup}, etc.) without requiring any
 * additional annotations from the boot module.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FreewayBootEntry {
}
