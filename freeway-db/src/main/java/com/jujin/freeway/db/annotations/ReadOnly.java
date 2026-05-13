package com.jujin.freeway.db.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marker for a read-only database handle (replica) in a read/write split
 * setup. Inject with {@code @ReadOnly Database db}.
 */
@Target({ PARAMETER, FIELD })
@Retention(RUNTIME)
@Documented
public @interface ReadOnly {
}
