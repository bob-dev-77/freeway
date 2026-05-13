package com.jujin.freeway.ioc.internal.util;

import com.jujin.freeway.ioc.Resource;
import java.net.URL;

/**
 * Implementation of {@link Resource} for files on the classpath (as defined by
 * a {@link ClassLoader}).
 */
public final class ClassPathResource extends AbstractResource {

    private final ClassLoader classLoader;

    // Guarded by synchronized
    private URL url;

    // Guarded by synchronized
    private boolean urlResolved;

    public ClassPathResource(String path) {
        this(Thread.currentThread().getContextClassLoader(), path);
    }

    public ClassPathResource(ClassLoader classLoader, String path) {
        super(path);
        assert classLoader != null;

        this.classLoader = classLoader;
    }

    @Override
    protected Resource newResource(String path) {
        return new ClassPathResource(classLoader, path);
    }

    @Override
    public URL toURL() {
        if (!urlResolved) {
            resolveURL();
        }

        return url;
    }

    private synchronized void resolveURL() {
        if (!urlResolved) {
            url = classLoader.getResource(getPath());

            validateURL(url);

            urlResolved = true;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;

        if (obj == this)
            return true;

        if (obj.getClass() != getClass())
            return false;

        ClassPathResource other = (ClassPathResource) obj;

        return (other.classLoader == classLoader &&
            other.getPath().equals(getPath()));
    }

    @Override
    public int hashCode() {
        return 227 ^ getPath().hashCode();
    }

    @Override
    public String toString() {
        return "classpath:" + getPath();
    }
}
