package com.jujin.freeway.ioc.internal.util;

import com.jujin.freeway.ioc.Resource;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract implementation of {@link Resource}. Subclasses must implement the
 * abstract methods {@link Resource#toURL()} and {@link #newResource(String)} as
 * well as toString(), hashCode() and equals().
 */
public abstract class AbstractResource implements Resource {

    private final String path;

    private final AtomicBoolean exists = new AtomicBoolean();

    private volatile boolean existsComputed;

    private final ConcurrentHashMap<Locale, Resource> localizationCache = new ConcurrentHashMap<>();

    protected AbstractResource(String path) {
        assert path != null;

        // Normalize paths to NOT start with a leading slash
        this.path = path.startsWith("/") ? path.substring(1) : path;
    }

    @Override
    public final String getPath() {
        return path;
    }

    @Override
    public final String getFile() {
        return extractFile(path);
    }

    private static String extractFile(String path) {
        int slashx = path.lastIndexOf('/');

        return path.substring(slashx + 1);
    }

    @Override
    public final String getFolder() {
        int slashx = path.lastIndexOf('/');

        return (slashx < 0) ? "" : path.substring(0, slashx);
    }

    @Override
    public final Resource forFile(String relativePath) {
        assert relativePath != null;

        List<String> terms = new ArrayList<>();

        for (String term : getFolder().split("/")) {
            terms.add(term);
        }

        // Handling systems using backslash as the path separator, such as Windows
        relativePath = relativePath.replace('\\', '/');

        for (String term : relativePath.split("/")) {
            // This will occur if the relative path contains sequential slashes

            if (term.equals("") || term.equals(".")) {
                continue;
            }

            if (term.equals("..")) {
                if (terms.isEmpty()) {
                    throw new IllegalStateException(
                        String.format(
                            "Relative path '%s' for %s would go above root.",
                            relativePath,
                            this));
                }

                terms.remove(terms.size() - 1);

                continue;
            }

            // TODO: term blank or otherwise invalid?
            // TODO: final term should not be "." or "..", or for that matter, the
            // name of a folder, since a Resource should be a file within
            // a folder.

            terms.add(term);
        }

        StringBuilder path = new StringBuilder(100);
        String sep = "";

        for (String term : terms) {
            path.append(sep).append(term);
            sep = "/";
        }

        return createResource(path.toString());
    }

    @Override
    public final Resource forLocale(Locale locale) {
        return localizationCache.computeIfAbsent(
            locale,
            this::findLocalizedResource);
    }

    private Resource findLocalizedResource(Locale locale) {
        for (String path : new LocalizedNameGenerator(this.path, locale)) {
            Resource potential = createResource(path);

            if (potential.exists())
                return potential;
        }

        return null;
    }

    @Override
    public final Resource withExtension(String extension) {
        assert InternalUtils.isNonBlank(extension);
        int dotx = path.lastIndexOf('.');

        if (dotx < 0)
            return createResource(path + "." + extension);

        return createResource(path.substring(0, dotx + 1) + extension);
    }

    /**
     * Creates a new resource, unless the path matches the current Resource's path
     * (in which case, this resource is returned).
     */
    private Resource createResource(String path) {
        if (this.path.equals(path))
            return this;

        return newResource(path);
    }

    /**
     * Simple check for whether {@link #toURL()} returns null or not.
     */
    @Override
    public boolean exists() {
        if (!existsComputed) {
            computeExists();
        }

        return exists.get();
    }

    private void computeExists() {
        synchronized (exists) {
            if (!existsComputed) {
                exists.set(toURL() != null);
                existsComputed = true;
            }
        }
    }

    /**
     * Obtains the URL for the Resource and opens the stream, wrapped by a
     * BufferedInputStream.
     */
    @Override
    public InputStream openStream() throws IOException {
        URL url = toURL();

        if (url == null) {
            return null;
        }
        if ("jar".equals(url.getProtocol())) {
            // Make sure that the URL does not reference a directory
            String urlAsString = url.toString();

            int indexOfExclamationMark = urlAsString.indexOf('!');

            String resourceInJar = urlAsString.substring(
                indexOfExclamationMark + 2);

            URL directoryResource = Thread.currentThread()
                .getContextClassLoader()
                .getResource(resourceInJar + "/");

            boolean isDirectory = directoryResource != null &&
                "jar".equals(directoryResource.getProtocol());

            if (isDirectory) {
                throw new IOException(
                    "Cannot open a stream for a resource that references a directory inside a JAR file (" +
                        url +
                        ").");
            }
        }

        return new BufferedInputStream(url.openStream());
    }

    /**
     * Factory method provided by subclasses.
     */
    protected abstract Resource newResource(String path);

    /**
     * Validates that the URL is correct; at this time, a correct URL is one of:
     * <ul>
     * <li>null</li>
     * <li>a non-file: URL</li>
     * <li>a file: URL where the case of the file matches the corresponding path
     * element</li>
     * </ul>
     *
     *
     * @param url
     *            to validate
     */
    protected void validateURL(URL url) {
        if (url == null) {
            return;
        }

        // Don't have to be concerned with the ClasspathURLConverter since this is
        // intended as a
        // runtime check during development; it's about ensuring that what works in
        // development on
        // a case-insensitive file system will work in production on the classpath (or
        // other case sensitive
        // file system).

        if (!url.getProtocol().equals("file")) {
            return;
        }

        File file = toFile(url);

        String expectedFileName = null;

        try {
            // On Windows, the canonical path uses backslash ('\') for the separator; an
            // easy hack
            // is to convert the platform file separator to match sane operating systems
            // (which use a foward slash).
            String sep = System.getProperty("file.separator");
            expectedFileName = extractFile(
                file.getCanonicalPath().replace(sep, "/"));
        } catch (IOException e) {
            return;
        }

        String actualFileName = getFile();

        if (actualFileName.equals(expectedFileName)) {
            return;
        }

        throw new IllegalStateException(
            String.format(
                "Resource %s does not match the case of the actual file name, '%s'.",
                this,
                expectedFileName));
    }

    private File toFile(URL url) {
        try {
            return new File(url.toURI());
        } catch (URISyntaxException ex) {
            return new File(url.getPath());
        }
    }

    @Override
    public boolean isVirtual() {
        return false;
    }
}
