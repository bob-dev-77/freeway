package com.jujin.freeway.ioc.classpath;

/**
 * Used to determine which files will be included in the set of matches paths
 * within a particular package.
 *
 * @see ClassPathScanner
 */
public interface ClassPathMatcher {
    /**
     * Invoked for each located file, to determine if it belongs. May be passed file
     * names that are actually nested folders. Typically, an implementation
     * determined what matches based on a file extension of naming pattern.
     *
     * @param packagePath
     *            package path containing the file, ending with '/'
     * @param fileName
     *            name of file within the package
     * @return true to include, false to exclude
     */
    boolean matches(String packagePath, String fileName);
}
