package com.jujin.freeway.ioc.internal;

import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.classpath.ClassNameLocator;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.classpath.ClassPathScanner;
import com.jujin.freeway.ioc.config.*;
import com.jujin.freeway.ioc.property.*;
import com.jujin.freeway.ioc.threading.*;
import com.jujin.freeway.ioc.classpath.*;
import com.jujin.freeway.ioc.exception.*;
import com.jujin.freeway.ioc.classpath.ClassPathMatcher;
import java.io.IOException;
import java.util.Collection;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ClassNameLocatorImpl implements ClassNameLocator {

    private final ClassPathScanner scanner;

    // This matches normal class files but not inner class files (which contain a
    // '$'.

    private final Pattern CLASS_NAME_PATTERN = Pattern.compile(
        "^\\p{javaJavaIdentifierStart}[\\p{javaJavaIdentifierPart}&&[^\\$]]*\\.class$",
        Pattern.CASE_INSENSITIVE);

    /**
     * Matches paths that are classes, but not for inner classes, or the
     * package-info.class psuedo-class (used for package-level annotations).
     */
    private final ClassPathMatcher CLASS_NAME_MATCHER = new ClassPathMatcher() {
        @Override
        public boolean matches(String packagePath, String fileName) {
            if (!CLASS_NAME_PATTERN.matcher(fileName).matches()) {
                return false;
            }

            // Filter out inner classes.

            if (fileName.contains("$") || fileName.equals("package-info.class")) {
                return false;
            }

            return true;
        }
    };

    /**
     * Maps a path name ("foo/bar/Baz.class") to a class name ("foo.bar.Baz").
     */
    private final Function<String, String> CLASS_NAME_MAPPER = element -> element.substring(0, element.length() - 6)
        .replace('/', '.');

    public ClassNameLocatorImpl(ClassPathScanner scanner) {
        this.scanner = scanner;
    }

    /**
     * Synchronization should not be necessary, but perhaps the underlying
     * ClassLoader's are sensitive to threading.
     */
    @Override
    public synchronized Collection<String> locate(
        String packageName) {
        String packagePath = packageName.replace('.', '/') + "/";

        try {
            Collection<String> matches = scanner.scan(
                packagePath,
                CLASS_NAME_MATCHER);

            return matches
                .stream()
                .map(CLASS_NAME_MAPPER)
                .collect(Collectors.toSet());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
