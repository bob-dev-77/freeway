package com.jujin.freeway.ioc.classpath;

import java.io.IOException;
import java.util.Set;

/**
 * Used to scan a portion of the classpath for files that match a particular
 * pattern, defined by a {@link ClassPathMatcher}.
 *
 */
public interface ClassPathScanner {
    /**
     * Perform a scan of the indicated package path and any nested packages.
     *
     * @param packagePath
     *            defines the root of the search as a path, e.g.,
     *            "com/jujin/freeway/ioc/" not "com.jujin.freeway.ioc"
     * @param matcher
     *            passed each potential match to determine which are included in the
     *            final result
     * @return matching paths based on the search and the matcher
     * @throws IOException
     *             if some error occurrs.
     */
    Set<String> scan(String packagePath, ClassPathMatcher matcher)
        throws IOException;
}
