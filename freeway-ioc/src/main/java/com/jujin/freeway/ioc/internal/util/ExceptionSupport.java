package com.jujin.freeway.ioc.internal.util;

import com.jujin.freeway.ioc.property.BeanPropertyAdapter;
import com.jujin.freeway.ioc.property.PropertyAccess;

import java.lang.annotation.Annotation;

/**
 * Exception introspection and message extraction utilities.
 */
public class ExceptionSupport {

    public static String toMessage(Throwable exception) {
        assert exception != null;
        String msg = exception.getMessage();
        return msg != null ? msg : exception.getClass().getName();
    }

    public static <T extends Throwable> T findCause(Throwable t, Class<T> type) {
        Throwable cur = t;
        while (cur != null) {
            if (type.isInstance(cur)) return type.cast(cur);
            cur = cur.getCause();
        }
        return null;
    }

    public static <T extends Throwable> T findCause(Throwable t, Class<T> type, PropertyAccess access) {
        Throwable cur = t;
        while (cur != null) {
            if (type.isInstance(cur)) return type.cast(cur);
            Throwable next = null;
            BeanPropertyAdapter adapter = access.getAdapter(cur);
            for (String name : adapter.getPropertyNames()) {
                Object val = adapter.getPropertyAdapter(name).get(cur);
                if (val != null && val != cur && val instanceof Throwable) {
                    next = (Throwable) val;
                    break;
                }
            }
            cur = next;
        }
        return null;
    }

    public static boolean isAnnotationInStackTrace(Throwable t, Class<? extends Annotation> annotationClass) {
        Throwable cur = t;
        while (cur != null) {
            if (cur.getClass().isAnnotationPresent(annotationClass)) return true;
            cur = cur.getCause();
        }
        return false;
    }

    private ExceptionSupport() {}
}
