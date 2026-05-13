package com.jujin.freeway.ioc.threading;

import com.jujin.freeway.ioc.Registry;

import java.util.Locale;

/**
 * Stores the locale <em>for the current thread</em>. This value persists until
 * {@link Registry#cleanupThread()} is invoked.
 */
public interface ThreadLocale {
    /**
     * Updates the locale for the current thread.
     *
     * @param locale
     *            the new locale (may not be null)
     */
    void setLocale(Locale locale);

    /**
     * Returns the thread's locale, which will be the JVM's default locale, until
     * {@link #setLocale(Locale)} is invoked.
     *
     * @return the thread's locale
     */
    Locale getLocale();
}
