package com.jujin.freeway.ioc.scan;

import java.net.URL;

/**
 * Used by {@link ClassNameLocator} to convert URLs from
 * one protocol to another. This is a hook for supporting OSGi, allowing
 * "bundleresource" and "bundleentry" protocols to be converted to "jar:" or
 * "file:".
 */
public interface ClassPathURLConverter {
    /**
     * Passed a URL provided by {@link ClassLoader#getResources(String)} to check to
     * see if can be converted.
     *
     * @param url
     *            to check
     * @return the url, or an equivalent url of type jar: or file:
     */
    URL convert(URL url);
}
