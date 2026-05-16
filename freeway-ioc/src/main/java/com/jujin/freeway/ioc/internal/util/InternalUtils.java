package com.jujin.freeway.ioc.internal.util;

import com.jujin.freeway.ioc.AnnotationProvider;
import com.jujin.freeway.ioc.ServiceLocator;
import com.jujin.freeway.ioc.annotations.ServiceId;
import com.jujin.freeway.ioc.property.BeanPropertyAdapter;
import com.jujin.freeway.ioc.property.PropertyAccess;
import java.io.Closeable;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Named;
import org.slf4j.Logger;

/**
 * Utilities used within various internal implementations of the freeway-ioc
 * module.
 * <p>
 * For injection resolution logic, see {@link InjectionPlanner} and
 * {@link InstancePlanBuilder}. For field injection, see {@link FieldInjector}.
 * Scope and configuration constants are in {@link Scopes} and
 * {@link IocConstants}.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class InternalUtils {

    /**
     * A null-object AnnotationProvider that always returns null for any annotation
     * type.
     */
    public static final AnnotationProvider NULL_ANNOTATION_PROVIDER =
        new AnnotationProvider() {
            @Override
            public <T extends Annotation> T getAnnotation(
                Class<T> annotationClass
            ) {
                return null;
            }
        };

    /** Pattern used to match non-word characters for stripping punctuation. */
    private static final Pattern NON_WORD_PATTERN = Pattern.compile("\\W");

    private static final Pattern NAME_PATTERN = Pattern.compile(
        "^[_|$]*([\\p{javaJavaIdentifierPart}]+?)[_|$]*$",
        Pattern.CASE_INSENSITIVE
    );

    // ── String / display utilities ──────────────────────────────────

    /**
     * Converts a method to a user presentable string consisting of the containing
     * class name, the method name, and the short form of the parameter list (the
     * class name of each parameter type, shorn of the package name portion).
     */
    public static String asString(Method method) {
        var buffer = new StringBuilder();
        buffer.append(method.getDeclaringClass().getName());
        buffer.append('.');
        buffer.append(method.getName());
        buffer.append('(');
        var paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            if (i > 0) buffer.append(", ");
            buffer.append(paramTypes[i].getSimpleName());
        }
        return buffer.append(')').toString();
    }

    /**
     * Returns the size of an object array, or 0 if the array is null.
     */
    public static int size(Object[] array) {
        return array == null ? 0 : array.length;
    }

    public static int size(Collection collection) {
        return collection == null ? 0 : collection.size();
    }

    /**
     * Strips leading "_" and "$" and trailing "_" from the name.
     */
    public static String stripMemberName(String memberName) {
        Matcher matcher = NAME_PATTERN.matcher(memberName);
        if (!matcher.matches()) throw new IllegalArgumentException(
            String.format(
                "Input '%s' is not a valid Java identifier.",
                memberName
            )
        );
        return matcher.group(1);
    }

    /**
     * Converts an enumeration (of Strings) into a sorted list of Strings.
     */
    public static List<String> toList(Enumeration e) {
        List<String> result = new ArrayList<>();

        while (e.hasMoreElements()) {
            var name = (String) e.nextElement();

            result.add(name);
        }

        Collections.sort(result);

        return result;
    }

    /**
     * Joins together some number of elements to form a comma separated list.
     */
    public static String join(List<?> elements) {
        return join(elements, ", ");
    }

    /**
     * Joins together some number of elements. If a value in the list is the empty
     * string, it is replaced with the string "(blank)".
     *
     * @param elements
     *            objects to be joined together
     * @param separator
     *            used between elements when joining
     */
    public static String join(List<?> elements, String separator) {
        switch (elements.size()) {
            case 0:
                return "";
            case 1:
                return String.valueOf(elements.get(0));
            default:
                var buffer = new StringBuilder();
                boolean first = true;

                for (Object o : elements) {
                    if (!first) buffer.append(separator);

                    String string = String.valueOf(o);

                    if (string.equals("")) string = "(blank)";

                    buffer.append(string);

                    first = false;
                }

                return buffer.toString();
        }
    }

    /**
     * Creates a sorted copy of the provided elements, then turns that into a comma
     * separated list.
     *
     * @return the elements converted to strings, sorted, joined with comma ... or
     *         "(none)" if the elements are null or empty
     */
    public static String joinSorted(Collection<?> elements) {
        if (elements == null || elements.isEmpty()) return "(none)";

        List<String> list = new ArrayList<>();

        for (Object o : elements) list.add(String.valueOf(o));

        Collections.sort(list);

        return join(list);
    }

    /**
     * Returns true if the input is null, or is a zero length string (excluding
     * leading/trailing whitespace).
     */
    public static boolean isBlank(String input) {
        return input == null || input.isBlank();
    }

    /**
     * Returns true if the input is an empty collection.
     */
    public static boolean isEmptyCollection(Object input) {
        if (input instanceof Collection) {
            return ((Collection) input).isEmpty();
        }

        return false;
    }

    /**
     * Returns true if the input is non-null and non-blank.
     */
    public static boolean isNonBlank(String input) {
        return input != null && !input.isBlank();
    }

    /**
     * Capitalizes a string, converting the first character to uppercase.
     */
    public static String capitalize(String input) {
        if (input.isEmpty()) return input;
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    public static <K, V> Set<K> keys(Map<K, V> map) {
        if (map == null) return Collections.emptySet();

        return map.keySet();
    }

    /**
     * Gets a value from a map (which may be null).
     */
    public static <K, V> V get(Map<K, V> map, K key) {
        if (map == null) return null;

        return map.get(key);
    }

    /**
     * Extracts the string keys from a map and returns them in sorted order.
     */
    public static List<String> sortedKeys(Map<?, ?> map) {
        if (map == null) return Collections.emptyList();

        List<String> keys = new ArrayList<>();
        for (Object o : map.keySet()) keys.add(String.valueOf(o));
        Collections.sort(keys);
        return keys;
    }

    /**
     * Returns true if the method provided is a static method.
     */
    public static boolean isStatic(Method method) {
        return Modifier.isStatic(method.getModifiers());
    }

    public static <T> Iterator<T> reverseIterator(final List<T> list) {
        final var normal = list.listIterator(list.size());

        return new Iterator<T>() {
            @Override
            public boolean hasNext() {
                return normal.hasPrevious();
            }

            @Override
            public T next() {
                return normal.previous();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * Return true if the input string contains the marker for symbols that must be
     * expanded.
     */
    public static boolean containsSymbols(String input) {
        return input.contains("${");
    }

    /**
     * Searches the string for the final period ('.') character and returns
     * everything after that. The input string is generally a fully qualified class
     * name, though freeway-core also uses this method for the occasional property
     * expression (which is also dot separated). Returns the input string unchanged
     * if it does not contain a period character.
     */
    public static String lastTerm(String input) {
        int dotx = input.lastIndexOf('.');
        return dotx < 0 ? input : input.substring(dotx + 1);
    }

    // ── Constructor / service-id utilities ──────────────────────────

    /**
     * Searches a class for the "best" constructor, the public constructor with the
     * most parameters. Returns null if there are no public constructors. If there
     * is more than one constructor with the maximum number of parameters, it is not
     * determined which will be returned (don't build a class like that!). In
     * addition, if a constructor is annotated with {@link javax.inject.Inject}, it
     * will be used (no check for multiple such constructors is made, only at most a
     * single constructor should have the annotation).
     *
     * @param clazz
     *            to search for a constructor for
     * @return the constructor to be used to instantiate the class, or null if no
     *         appropriate constructor was found
     */
    public static Constructor findAutobuildConstructor(Class clazz) {
        Constructor[] constructors = clazz.getConstructors();

        switch (constructors.length) {
            case 1:
                return constructors[0];
            case 0:
                return null;
            default:
                break;
        }

        var standardConstructor = findConstructorByAnnotation(
            constructors,
            javax.inject.Inject.class
        );

        if (standardConstructor != null) {
            return standardConstructor;
        }

        var fwStandardConstructor = findConstructorByAnnotation(
            constructors,
            com.jujin.freeway.ioc.annotations.Inject.class
        );

        if (fwStandardConstructor != null) {
            return fwStandardConstructor;
        }

        // Choose a constructor with the most parameters.

        Arrays.sort(
            constructors,
            (o1, o2) ->
                o2.getParameterTypes().length - o1.getParameterTypes().length
        );

        return constructors[0];
    }

    private static <
        T extends Annotation
    > Constructor findConstructorByAnnotation(
        Constructor[] constructors,
        Class<T> annotationClass
    ) {
        for (Constructor c : constructors) {
            if (c.getAnnotation(annotationClass) != null) return c;
        }

        return null;
    }

    /**
     * Validates that the constructor can be used for autobuilding.
     */
    public static void validateConstructorForAutobuild(
        Constructor constructor
    ) {
        Class clazz = constructor.getDeclaringClass();

        if (
            !Modifier.isPublic(clazz.getModifiers())
        ) throw new IllegalArgumentException(
            String.format(
                "Class %s is not a public class and may not be autobuilt.",
                clazz.getName()
            )
        );

        if (
            !Modifier.isPublic(constructor.getModifiers())
        ) throw new IllegalArgumentException(
            String.format(
                "Constructor %s is not public and may not be used for autobuilding an instance of the class. " +
                    "You should make the constructor public, or mark an alternate public constructor with the @Inject annotation.",
                constructor
            )
        );
    }

    // ── Map utilities ───────────────────────────────────────────────

    /**
     * Adds a value to a specially organized map where the values are lists of
     * objects. This somewhat simulates a map that allows multiple values for the
     * same key.
     */
    public static <K, V> void addToMapList(
        Map<K, List<V>> map,
        K key,
        V value
    ) {
        List<V> list = map.get(key);

        if (list == null) {
            list = new ArrayList<>();
            map.put(key, list);
        }

        list.add(value);
    }

    // ── Annotation utilities ────────────────────────────────────────

    /**
     * Validates that the marker annotation class has a retention policy of runtime.
     */
    public static void validateMarkerAnnotation(Class markerClass) {
        var policy = (Retention) markerClass.getAnnotation(Retention.class);

        if (policy != null && policy.value() == RetentionPolicy.RUNTIME) return;

        throw new IllegalArgumentException(
            UtilMessages.badMarkerAnnotation(markerClass)
        );
    }

    public static void validateMarkerAnnotations(Class[] markerClasses) {
        for (Class markerClass : markerClasses)
            validateMarkerAnnotation(markerClass);
    }

    // ── I/O utilities ───────────────────────────────────────────────

    public static void close(Closeable stream) {
        if (stream != null) try {
            stream.close();
        } catch (IOException ex) {
            // Ignore.
        }
    }

    // ── Exception utilities ─────────────────────────────────────────

    /**
     * Extracts the message from an exception. If the exception's message is null,
     * returns the exception's class name.
     */
    public static String toMessage(Throwable exception) {
        assert exception != null;

        String message = exception.getMessage();

        if (message != null) return message;

        return exception.getClass().getName();
    }

    /**
     * Locates a particular type of exception, working its way via the cause
     * property of each exception in the exception stack.
     */
    public static <T extends Throwable> T findCause(
        Throwable t,
        Class<T> type
    ) {
        Throwable current = t;

        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }

            current = current.getCause();
        }

        return null;
    }

    /**
     * Locates a particular type of exception, working its way down via any property
     * that returns some type of Exception. This is more expensive, but more
     * accurate, than {@link #findCause(Throwable, Class)} as it works with older
     * exceptions that do not properly implement the (relatively new) cause
     * property.
     */
    public static <T extends Throwable> T findCause(
        Throwable t,
        Class<T> type,
        PropertyAccess access
    ) {
        Throwable current = t;

        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }

            Throwable next = null;

            BeanPropertyAdapter adapter = access.getAdapter(current);

            for (String name : adapter.getPropertyNames()) {
                Object value = adapter.getPropertyAdapter(name).get(current);

                if (
                    value != null &&
                    value != current &&
                    value instanceof Throwable
                ) {
                    next = (Throwable) value;
                    break;
                }
            }

            current = next;
        }

        return null;
    }

    /**
     * Tells whether an exception annotated with a given annotation is found in the
     * stack trace.
     */
    public static boolean isAnnotationInStackTrace(
        Throwable t,
        Class<? extends Annotation> annotationClass
    ) {
        boolean answer = false;
        Throwable current = t;

        while (current != null) {
            if (current.getClass().isAnnotationPresent(annotationClass)) {
                answer = true;
                break;
            }

            current = current.getCause();
        }

        return answer;
    }

    // ── Class instantiation utilities ───────────────────────────────

    /**
     * Instantiates a contribution class. If the contribution type is an interface
     * and the class is local, the instance will be proxied. Otherwise, it will be
     * autobuilt.
     */
    public static <T> T instantiate(
        Class<T> contributionType,
        ServiceLocator locator,
        Class<? extends T> clazz
    ) {
        assert clazz != null;

        if (
            contributionType.isInterface() &&
            InternalUtils.isLocalFile(clazz) &&
            contributionType.isAssignableFrom(clazz)
        ) return locator.proxy(contributionType, clazz);

        return locator.autobuild(clazz);
    }

    // ── AnnotationProvider / reflection utilities ───────────────────

    /**
     * Extracts the service id from the passed annotated element. First the
     * {@link ServiceId} annotation is checked. If present, its value is returned.
     * Otherwise {@link Named} annotation is checked. If present, its value is
     * returned. If neither of the annotations is present, <code>null</code> value
     * is returned.
     */
    public static String getServiceId(AnnotatedElement annotated) {
        var serviceIdAnnotation = annotated.getAnnotation(ServiceId.class);

        if (serviceIdAnnotation != null) {
            return serviceIdAnnotation.value();
        }

        var namedAnnotation = annotated.getAnnotation(Named.class);

        if (namedAnnotation != null) {
            var value = namedAnnotation.value();

            if (isNonBlank(value)) {
                return value;
            }
        }

        return null;
    }

    /**
     * Converts a class to a user presentable type name.
     */
    public static String toSimpleTypeName(Class<?> type) {
        if (type == null) return "null";
        if (type.isArray()) return (
            toSimpleTypeName(type.getComponentType()) + "[]"
        );
        var name = type.getCanonicalName();
        return name != null ? name : type.getName();
    }

    /**
     * Converts an array of classes into an array of user presentable type names.
     */
    public static String[] toSimpleTypeNames(Class<?>[] types) {
        String[] result = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            result[i] = toSimpleTypeName(types[i]);
        }
        return result;
    }

    /**
     */
    public static final Function<Class, AnnotationProvider> CLASS_TO_AP_MAPPER =
        InternalUtils::toAnnotationProvider;

    /**
     */
    public static AnnotationProvider toAnnotationProvider(
        final Class<?> element
    ) {
        return new AnnotationProvider() {
            @Override
            public <T extends Annotation> T getAnnotation(
                Class<T> annotationClass
            ) {
                return annotationClass.cast(
                    element.getAnnotation(annotationClass)
                );
            }
        };
    }

    /**
     */
    public static final Function<
        Method,
        AnnotationProvider
    > METHOD_TO_AP_MAPPER = InternalUtils::toAnnotationProvider;

    public static final Method findMethod(
        Class containingClass,
        String methodName,
        Class... parameterTypes
    ) {
        if (containingClass == null) return null;

        try {
            return containingClass.getMethod(methodName, parameterTypes);
        } catch (SecurityException ex) {
            throw new RuntimeException(ex);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    /**
     */
    public static <T extends Comparable<T>> List<T> matchAndSort(
        Collection<? extends T> collection,
        Predicate<T> predicate
    ) {
        assert predicate != null;

        return collection
            .stream()
            .filter(predicate)
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Determines if the indicated class is stored as a locally accessible file (and
     * not, typically, as a file inside a JAR). This is related to automatic
     * reloading of services.
     */
    public static boolean isLocalFile(Class clazz) {
        var path = clazz.getName().replace('.', '/') + ".class";

        ClassLoader loader = clazz.getClassLoader();

        if (loader == null) return false;

        var classFileURL = loader.getResource(path);

        return (
            classFileURL != null && classFileURL.getProtocol().equals("file")
        );
    }

    public static AnnotationProvider toAnnotationProvider(
        final Method element
    ) {
        if (element == null) return NULL_ANNOTATION_PROVIDER;

        return new AnnotationProvider() {
            @Override
            public <T extends Annotation> T getAnnotation(
                Class<T> annotationClass
            ) {
                return element.getAnnotation(annotationClass);
            }
        };
    }

    // ── Presentation utilities ──────────────────────────────────────

    /**
     * Capitalizes the string, and inserts a space before each upper case character
     * (or sequence of upper case characters). Thus "userId" becomes "User Id", etc.
     * Also, converts underscore into space (and capitalizes the following word),
     * thus "user_id" also becomes "User Id".
     */
    public static String toUserPresentable(String id) {
        var builder = new StringBuilder(id.length() * 2);

        char[] chars = id.toCharArray();
        boolean postSpace = true;
        boolean upcaseNext = true;

        for (char ch : chars) {
            if (upcaseNext) {
                builder.append(Character.toUpperCase(ch));
                upcaseNext = false;
                continue;
            }

            if (ch == '_') {
                builder.append(' ');
                upcaseNext = true;
                continue;
            }

            boolean upperCase = Character.isUpperCase(ch);

            if (upperCase && !postSpace) builder.append(' ');

            builder.append(ch);

            postSpace = upperCase;
        }

        return builder.toString();
    }

    /**
     * Used to convert a property expression into a key that can be used to locate
     * various resources (Blocks, messages, etc.). Strips out any punctuation
     * characters, leaving just word characters (letters, numbers and the
     * underscore).
     */
    public static String extractIdFromPropertyExpression(String expression) {
        return replace(expression, NON_WORD_PATTERN, "");
    }

    /**
     * Looks for a label within the messages based on the id. If found, it is used,
     * otherwise the name is converted to a user presentable form.
     */
    public static String defaultLabel(
        String id,
        java.util.function.Function<String, String> messages,
        String propertyExpression
    ) {
        String key = id + "-label";
        String label = messages.apply(key);

        if (label != null) return label;

        return toUserPresentable(
            extractIdFromPropertyExpression(lastTerm(propertyExpression))
        );
    }

    public static String replace(
        String input,
        Pattern pattern,
        String replacement
    ) {
        return pattern.matcher(input).replaceAll(replacement);
    }

    private InternalUtils() {}
}
