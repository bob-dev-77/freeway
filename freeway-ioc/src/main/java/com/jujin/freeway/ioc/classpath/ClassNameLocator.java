package com.jujin.freeway.ioc.classpath;

import java.util.Collection;

/**
 * Scans the classpath for top-level classes within particular packages.
 *
 * @see ClassPathURLConverter
 * @see ClassPathScanner
 */
public interface ClassNameLocator {
    /**
     * Searches for all classes under the given package name. This consists of all
     * top-level classes in the indicated package (or any sub-package), but excludes
     * inner classes. No other filtering (beyond inner classes) occurs, so there's
     * no guarantee that the class names returned are public (for example).
     *
     * @param packageName
     *            the name of the package to be inspected.
     * @return fully qualified class names
     */
    Collection<String> locate(String packageName);
}
