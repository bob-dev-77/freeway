package com.jujin.freeway.ioc.internal.util;

import com.jujin.freeway.ioc.*;
import com.jujin.freeway.ioc.internal.IdMatcher;
import com.jujin.freeway.ioc.lifecycle.*;
import com.jujin.freeway.ioc.property.BeanPropertyAdapter;
import com.jujin.freeway.ioc.advisor.OperationTracker;
import com.jujin.freeway.ioc.property.PropertyAccess;
import com.jujin.freeway.ioc.annotations.*;

import java.io.Closeable;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.*;
import java.util.*;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.inject.Named;
import org.slf4j.Logger;

/**
 * Utilities used within various internal implementations of the freeway-ioc
 * module.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class InternalUtils {

    /**
     * Name of a JVM System Property (but not, alas, a configuration symbol) that is
     * used to disable live service reloading entirely (i.e., reverting to Freeway
     * 5.1 behavior).
     */
    public static final boolean SERVICE_CLASS_RELOADING_ENABLED = Boolean.parseBoolean(
        System.getProperty("freeway.service-reloading-enabled", "true"));

    /** Manifest entry name used to identify Freeway module classes. */
    public static final String MODULE_BUILDER_MANIFEST_ENTRY_NAME = "Freeway-Module-Classes";

    /** Service ID for the object injector. */
    public static final String INJECTOR_SERVICE_ID = "ObjectInjector";

    /** Configuration symbol for the thread pool core size. */
    public static final String THREAD_POOL_CORE_SIZE = "freeway.thread-pool.core-pool-size";

    /** Configuration symbol for the thread pool max size. */
    public static final String THREAD_POOL_MAX_SIZE = "freeway.thread-pool.max-pool-size";

    /** Configuration symbol for the thread pool keep-alive time. */
    public static final String THREAD_POOL_KEEP_ALIVE = "freeway.thread-pool.keep-alive";

    /** Configuration symbol for whether the thread pool is enabled. */
    public static final String THREAD_POOL_ENABLED = "freeway.thread-pool-enabled";

    /** Configuration symbol for the thread pool queue size. */
    public static final String THREAD_POOL_QUEUE_SIZE = "freeway.thread-pool.queue-size";

    /** Configuration symbol for the proxy mechanism. */
    public static final String PROXY_MECHANISM = "freeway.proxy-mechanism";

    /** The default scope name. */
    public static final String DEFAULT = "singleton";

    /** The perthread scope name. */
    public static final String PERTHREAD = "perthread";

    /**
     * A null-object AnnotationProvider that always returns null for any annotation
     * type.
     */
    private static final AnnotationProvider NULL_ANNOTATION_PROVIDER = new AnnotationProvider() {
        @Override
        public <T extends Annotation> T getAnnotation(
            Class<T> annotationClass) {
            return null;
        }
    };

    /** Pattern used to match non-word characters for stripping punctuation. */
    private static final Pattern NON_WORD_PATTERN = Pattern.compile("\\W");

    private static final Pattern NAME_PATTERN = Pattern.compile(
        "^[_|$]*([\\p{javaJavaIdentifierPart}]+?)[_|$]*$",
        Pattern.CASE_INSENSITIVE);

    /**
     * Converts a method to a user presentable string consisting of the containing
     * class name, the method name, and the short form of the parameter list (the
     * class name of each parameter type, shorn of the package name portion).
     *
     * @param method
     * @return short string representation
     */
    public static String asString(Method method) {
        var buffer = new StringBuilder();
        buffer.append(method.getDeclaringClass().getName());
        buffer.append('.');
        buffer.append(method.getName());
        buffer.append('(');
        var paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            if (i > 0)
                buffer.append(", ");
            buffer.append(paramTypes[i].getSimpleName());
        }
        return buffer.append(')').toString();
    }

    /**
     * Returns the size of an object array, or null if the array is empty.
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
        if (!matcher.matches())
            throw new IllegalArgumentException(
                String.format(
                    "Input '%s' is not a valid Java identifier.",
                    memberName));
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
     * Finds a specific annotation type within an array of annotations.
     *
     * @param <T>
     * @param annotations
     *            to search
     * @param annotationClass
     *            to match
     * @return the annotation instance, if found, or null otherwise
     */
    public static <T extends Annotation> T findAnnotation(
        Annotation[] annotations,
        Class<T> annotationClass) {
        for (Annotation a : annotations) {
            if (annotationClass.isInstance(a))
                return annotationClass.cast(a);
        }

        return null;
    }

    private static ObjectCreator<Object> asObjectCreator(
        final Object fixedValue) {
        return () -> fixedValue;
    }

    private static ObjectCreator calculateInjection(
        final Class injectionType,
        Type genericType,
        final Annotation[] annotations,
        final ServiceLocator locator,
        InjectionResources resources) {
        final var provider = new AnnotationProvider() {
            @Override
            public <T extends Annotation> T getAnnotation(
                Class<T> annotationClass) {
                return findAnnotation(annotations, annotationClass);
            }
        };

        Named named = provider.getAnnotation(Named.class);

        if (named != null) {
            return asObjectCreator(
                locator.getService(named.value(), injectionType));
        }

        // Check Freeway's @Inject annotation
        com.jujin.freeway.ioc.annotations.Inject fwInject = provider.getAnnotation(
            com.jujin.freeway.ioc.annotations.Inject.class);

        if (fwInject != null) {
            String sid = fwInject.value();
            if (sid != null && !sid.isEmpty()) {
                return asObjectCreator(locator.getService(sid, injectionType));
            }
            // no value → type-based, fall through to getObject
        }

        // When no @Named is present: if @Inject (javax or Freeway) is found, skip
        // resource lookups
        // and go directly to ObjectInjector.

        if (provider.getAnnotation(javax.inject.Inject.class) == null &&
            fwInject == null) {
            Object result = resources.findResource(injectionType, genericType);

            if (result != null) {
                return asObjectCreator(result);
            }
        }

        // For @Autobuild, special case where we always compute a fresh value
        // for the injection on every use. Elsewhere, we compute once when generating
        // the
        // construction plan and just use the singleton value repeatedly.

        if (provider.getAnnotation(Autobuild.class) != null) {
            return () -> locator.getObject(injectionType, provider);
        }

        // Otherwise, make use of the ObjectInjector service to resolve this type (plus
        // any other information gleaned from additional annotation) into the correct
        // object.

        return asObjectCreator(locator.getObject(injectionType, provider));
    }

    public static ObjectCreator[] calculateParametersForMethod(
        Method method,
        ServiceLocator locator,
        InjectionResources resources,
        OperationTracker tracker) {
        return calculateParameters(
            locator,
            resources,
            method.getParameterTypes(),
            method.getGenericParameterTypes(),
            method.getParameterAnnotations(),
            tracker);
    }

    public static ObjectCreator[] calculateParameters(
        final ServiceLocator locator,
        final InjectionResources resources,
        Class[] parameterTypes,
        final Type[] genericTypes,
        Annotation[][] parameterAnnotations,
        OperationTracker tracker) {
        int parameterCount = parameterTypes.length;

        ObjectCreator[] parameters = new ObjectCreator[parameterCount];

        for (int i = 0; i < parameterCount; i++) {
            final Class type = parameterTypes[i];
            final Type genericType = genericTypes[i];
            final Annotation[] annotations = parameterAnnotations[i];

            var description = String.format(
                "Determining injection value for parameter #%d (%s)",
                i + 1,
                toSimpleTypeName(type));

            final Supplier<ObjectCreator> operation = () -> calculateInjection(
                type,
                genericType,
                annotations,
                locator,
                resources);

            parameters[i] = tracker.invoke(description, operation);
        }

        return parameters;
    }

    /**
     * Injects into the fields (of all visibilities) when the
     * {@link javax.inject.Inject} annotation is present. {@link javax.inject.Named}
     * can be used alongside to specify the service id.
     *
     * @param object
     *            to be initialized
     * @param locator
     *            used to resolve external dependencies
     * @param resources
     *            provides injection resources for fields
     * @param tracker
     *            track operations
     */
    public static void injectIntoFields(
        final Object object,
        final ServiceLocator locator,
        final InjectionResources resources,
        OperationTracker tracker) {
        Class clazz = object.getClass();

        while (clazz != Object.class) {
            Field[] fields = clazz.getDeclaredFields();

            for (final Field f : fields) {
                // Ignore all static and final fields.

                int fieldModifiers = f.getModifiers();

                if (Modifier.isStatic(fieldModifiers) ||
                    Modifier.isFinal(fieldModifiers))
                    continue;

                final var ap = new AnnotationProvider() {
                    @Override
                    public <T extends Annotation> T getAnnotation(
                        Class<T> annotationClass) {
                        return f.getAnnotation(annotationClass);
                    }
                };

                var description = String.format(
                    "Calculating possible injection value for field %s.%s (%s)",
                    clazz.getName(),
                    f.getName(),
                    toSimpleTypeName(f.getType()));

                tracker.run(
                    description,
                    new Runnable() {
                        @Override
                        public void run() {
                            final Class<?> fieldType = f.getType();

                            com.jujin.freeway.ioc.annotations.Inject fwFieldInject = ap.getAnnotation(
                                com.jujin.freeway.ioc.annotations.Inject.class);

                            if (ap.getAnnotation(javax.inject.Inject.class) != null ||
                                fwFieldInject != null) {
                                // Check Freeway @Inject value first
                                if (fwFieldInject != null) {
                                    String sid = fwFieldInject.value();
                                    if (sid != null && !sid.isEmpty()) {
                                        inject(
                                            object,
                                            f,
                                            locator.getService(sid, fieldType));
                                        return;
                                    }
                                }

                                Named named = ap.getAnnotation(Named.class);

                                if (named == null) {
                                    Object value = resources.findResource(
                                        fieldType,
                                        f.getGenericType());

                                    if (value != null) {
                                        inject(object, f, value);
                                        return;
                                    }

                                    inject(
                                        object,
                                        f,
                                        locator.getObject(fieldType, ap));
                                } else {
                                    inject(
                                        object,
                                        f,
                                        locator.getService(
                                            named.value(),
                                            fieldType));
                                }

                                return;
                            }

                            // Ignore fields that do not have the necessary annotation.
                        }
                    });
            }

            clazz = clazz.getSuperclass();
        }
    }

    private static void inject(Object target, Field field, Object value) {
        try {
            MethodHandleUtils.varHandle(field).set(target, value);
        } catch (Exception ex) {
            throw new RuntimeException(
                String.format(
                    "Unable to set field '%s' of %s to %s: %s",
                    field.getName(),
                    target,
                    value,
                    toMessage(ex)));
        }
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
                    if (!first)
                        buffer.append(separator);

                    String string = String.valueOf(o);

                    if (string.equals(""))
                        string = "(blank)";

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
        if (elements == null || elements.isEmpty())
            return "(none)";

        List<String> list = new ArrayList<>();

        for (Object o : elements)
            list.add(String.valueOf(o));

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

    public static boolean isNonBlank(String input) {
        return input != null && !input.isBlank();
    }

    /**
     * Capitalizes a string, converting the first character to uppercase.
     */
    public static String capitalize(String input) {
        if (input.isEmpty())
            return input;
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    public static <K, V> Set<K> keys(Map<K, V> map) {
        if (map == null)
            return Collections.emptySet();

        return map.keySet();
    }

    /**
     * Gets a value from a map (which may be null).
     *
     * @param <K>
     * @param <V>
     * @param map
     *            the map to extract from (may be null)
     * @param key
     * @return the value from the map, or null if the map is null
     */

    public static <K, V> V get(Map<K, V> map, K key) {
        if (map == null)
            return null;

        return map.get(key);
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
            javax.inject.Inject.class);

        if (standardConstructor != null) {
            return standardConstructor;
        }

        var fwStandardConstructor = findConstructorByAnnotation(
            constructors,
            com.jujin.freeway.ioc.annotations.Inject.class);

        if (fwStandardConstructor != null) {
            return fwStandardConstructor;
        }

        // Choose a constructor with the most parameters.

        Arrays.sort(
            constructors,
            (o1, o2) -> o2.getParameterTypes().length - o1.getParameterTypes().length);

        return constructors[0];
    }

    private static <T extends Annotation> Constructor findConstructorByAnnotation(
        Constructor[] constructors,
        Class<T> annotationClass) {
        for (Constructor c : constructors) {
            if (c.getAnnotation(annotationClass) != null)
                return c;
        }

        return null;
    }

    /**
     * Adds a value to a specially organized map where the values are lists of
     * objects. This somewhat simulates a map that allows multiple values for the
     * same key.
     *
     * @param map
     *            to store value into
     * @param key
     *            for which a value is added
     * @param value
     *            to add
     * @param <K>
     *            the type of key
     * @param <V>
     *            the type of the list
     */
    public static <K, V> void addToMapList(
        Map<K, List<V>> map,
        K key,
        V value) {
        List<V> list = map.get(key);

        if (list == null) {
            list = new ArrayList<>();
            map.put(key, list);
        }

        list.add(value);
    }

    /**
     * Validates that the marker annotation class had a retention policy of runtime.
     *
     * @param markerClass
     *            the marker annotation class
     */
    public static void validateMarkerAnnotation(Class markerClass) {
        var policy = (Retention) markerClass.getAnnotation(Retention.class);

        if (policy != null && policy.value() == RetentionPolicy.RUNTIME)
            return;

        throw new IllegalArgumentException(
            UtilMessages.badMarkerAnnotation(markerClass));
    }

    public static void validateMarkerAnnotations(Class[] markerClasses) {
        for (Class markerClass : markerClasses)
            validateMarkerAnnotation(markerClass);
    }

    public static void close(Closeable stream) {
        if (stream != null)
            try {
                stream.close();
            } catch (IOException ex) {
                // Ignore.
            }
    }

    /**
     * Extracts the message from an exception. If the exception's message is null,
     * returns the exceptions class name.
     *
     * @param exception
     *            to extract message from
     * @return message or class name
     */
    public static String toMessage(Throwable exception) {
        assert exception != null;

        String message = exception.getMessage();

        if (message != null)
            return message;

        return exception.getClass().getName();
    }

    /**
     * Locates a particular type of exception, working its way via the cause
     * property of each exception in the exception stack.
     *
     * @param t
     *            the outermost exception
     * @param type
     *            the type of exception to search for
     * @return the first exception of the given type, if found, or null
     */
    public static <T extends Throwable> T findCause(
        Throwable t,
        Class<T> type) {
        Throwable current = t;

        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }

            // Not a match, work down.

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
     *
     * @param t
     *            the outermost exception
     * @param type
     *            the type of exception to search for
     * @param access
     *            used to access properties
     * @return the first exception of the given type, if found, or null
     */
    public static <T extends Throwable> T findCause(
        Throwable t,
        Class<T> type,
        PropertyAccess access) {
        Throwable current = t;

        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }

            Throwable next = null;

            BeanPropertyAdapter adapter = access.getAdapter(current);

            for (String name : adapter.getPropertyNames()) {
                Object value = adapter.getPropertyAdapter(name).get(current);

                if (value != null &&
                    value != current &&
                    value instanceof Throwable) {
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
     *
     * @return <code>true</code> or <code>false</code>
     */
    public static boolean isAnnotationInStackTrace(
        Throwable t,
        Class<? extends Annotation> annotationClass) {
        boolean answer = false;
        Throwable current = t;

        while (current != null) {
            if (current.getClass().isAnnotationPresent(annotationClass)) {
                answer = true;
                break;
            }

            // Not a match, work down.

            current = current.getCause();
        }

        return answer;
    }

    /**
     * Instantiates a contribution class. If the contribution type is an interface
     * and the class is local, the instance will be proxied. Otherwise, it will be
     * autobuilt.
     *
     * @param contributionType
     *            the expected type of the contribution
     * @param locator
     *            the object locator for autobuilding/proxying
     * @param clazz
     *            the implementation class to instantiate
     * @param <T>
     *            the contribution type
     * @return the instantiated instance
     */
    public static <T> T instantiate(
        Class<T> contributionType,
        ServiceLocator locator,
        Class<? extends T> clazz) {
        assert clazz != null;

        // Only attempt to proxy the class if it is the right type for the contribution.
        // Starting
        // in 5.3, it is allowed to make contributions of different types (as long as
        // they can be
        // coerced to the right type) ... but this means that sometimes, a class is
        // passed that isn't
        // assignable to the actual contribution type.

        if (contributionType.isInterface() &&
            InternalUtils.isLocalFile(clazz) &&
            contributionType.isAssignableFrom(clazz))
            return locator.proxy(contributionType, clazz);

        return locator.autobuild(clazz);
    }

    public static void validateConstructorForAutobuild(
        Constructor constructor) {
        Class clazz = constructor.getDeclaringClass();

        if (!Modifier.isPublic(clazz.getModifiers()))
            throw new IllegalArgumentException(
                String.format(
                    "Class %s is not a public class and may not be autobuilt.",
                    clazz.getName()));

        if (!Modifier.isPublic(constructor.getModifiers()))
            throw new IllegalArgumentException(
                String.format(
                    "Constructor %s is not public and may not be used for autobuilding an instance of the class. " +
                        "You should make the constructor public, or mark an alternate public constructor with the @Inject annotation.",
                    constructor));
    }

    /**
     */
    public static final Function<Class, AnnotationProvider> CLASS_TO_AP_MAPPER = InternalUtils::toAnnotationProvider;

    /**
     */
    public static AnnotationProvider toAnnotationProvider(
        final Class<?> element) {
        return new AnnotationProvider() {
            @Override
            public <T extends Annotation> T getAnnotation(
                Class<T> annotationClass) {
                return annotationClass.cast(
                    element.getAnnotation(annotationClass));
            }
        };
    }

    /**
     */
    public static final Function<Method, AnnotationProvider> METHOD_TO_AP_MAPPER = InternalUtils::toAnnotationProvider;

    public static final Method findMethod(
        Class containingClass,
        String methodName,
        Class... parameterTypes) {
        if (containingClass == null)
            return null;

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
        Predicate<T> predicate) {
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
     *
     */
    public static boolean isLocalFile(Class clazz) {
        var path = clazz.getName().replace('.', '/') + ".class";

        ClassLoader loader = clazz.getClassLoader();

        // System classes have no visible class loader, and are not local files.

        if (loader == null)
            return false;

        var classFileURL = loader.getResource(path);

        return (classFileURL != null && classFileURL.getProtocol().equals("file"));
    }

//    /**
//     * Wraps a {@link Coercion} as a {@link Mapper}.
//     *
//     */
//    public static <S, T> Function<S, T> toMapper(
//        final Coercion<S, T> coercion) {
//        assert coercion != null;
//
//        return coercion::coerce;
//    }
//
//    private static final AtomicLong uuidGenerator = new AtomicLong(
//        System.nanoTime());
//
//    /**
//     * Generates a unique value for the current execution of the application. This
//     * initial UUID value is not easily predictable; subsequent UUIDs are allocated
//     * in ascending series.
//     *
//     */
//    public static long nextUUID() {
//        return uuidGenerator.incrementAndGet();
//    }

    /**
     * Extracts the service id from the passed annotated element. First the
     * {@link ServiceId} annotation is checked. If present, its value is returned.
     * Otherwise {@link Named} annotation is checked. If present, its value is
     * returned. If neither of the annotations is present, <code>null</code> value
     * is returned
     *
     * @param annotated
     *            annotated element to get annotations from
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

    public static AnnotationProvider toAnnotationProvider(
        final Method element) {
        if (element == null)
            return NULL_ANNOTATION_PROVIDER;

        return new AnnotationProvider() {
            @Override
            public <T extends Annotation> T getAnnotation(
                Class<T> annotationClass) {
                return element.getAnnotation(annotationClass);
            }
        };
    }

    public static <T> ObjectCreator<T> createConstructorConstructionPlan(
        final OperationTracker tracker,
        final ServiceLocator locator,
        final InjectionResources resources,
        final Logger logger,
        final String description,
        final Constructor<T> constructor) {
        return tracker.invoke(
            String.format(
                "Creating plan to instantiate %s via %s",
                constructor.getDeclaringClass().getName(),
                constructor),
            () -> {
                validateConstructorForAutobuild(constructor);

                ObjectCreator[] constructorParameters = calculateParameters(
                    locator,
                    resources,
                    constructor.getParameterTypes(),
                    constructor.getGenericParameterTypes(),
                    constructor.getParameterAnnotations(),
                    tracker);

                var core = (Supplier<T>) () -> invokeConstructor(
                    MethodHandleUtils.constructorHandle(constructor),
                    constructorParameters);

                var wrapped = logger == null
                    ? core
                    : new LoggingInvokableWrapper<T>(
                        logger,
                        description,
                        core);

                ConstructionPlan<T> plan = new ConstructionPlan(
                    tracker,
                    description,
                    wrapped);

                extendPlanForInjectedFields(
                    plan,
                    tracker,
                    locator,
                    resources,
                    constructor.getDeclaringClass());

                extendPlanForPostInjectionMethods(
                    plan,
                    tracker,
                    locator,
                    resources,
                    constructor.getDeclaringClass());

                return plan;
            });
    }

    private static <T> void extendPlanForInjectedFields(
        final ConstructionPlan<T> plan,
        OperationTracker tracker,
        final ServiceLocator locator,
        final InjectionResources resources,
        Class<T> instantiatedClass) {
        Class clazz = instantiatedClass;

        while (clazz != Object.class) {
            Field[] fields = clazz.getDeclaredFields();

            for (final Field f : fields) {
                // Ignore all static and final fields.

                int fieldModifiers = f.getModifiers();

                if (Modifier.isStatic(fieldModifiers) ||
                    Modifier.isFinal(fieldModifiers))
                    continue;

                final var ap = new AnnotationProvider() {
                    @Override
                    public <T extends Annotation> T getAnnotation(
                        Class<T> annotationClass) {
                        return f.getAnnotation(annotationClass);
                    }
                };

                var description = String.format(
                    "Calculating possible injection value for field %s.%s (%s)",
                    clazz.getName(),
                    f.getName(),
                    toSimpleTypeName(f.getType()));

                tracker.run(
                    description,
                    new Runnable() {
                        @Override
                        public void run() {
                            final Class<?> fieldType = f.getType();

                            com.jujin.freeway.ioc.annotations.Inject fwFieldInject = ap.getAnnotation(
                                com.jujin.freeway.ioc.annotations.Inject.class);

                            if (ap.getAnnotation(javax.inject.Inject.class) != null ||
                                fwFieldInject != null) {
                                // Check Freeway @Inject value first
                                if (fwFieldInject != null) {
                                    String sid = fwFieldInject.value();
                                    if (sid != null && !sid.isEmpty()) {
                                        addInjectPlan(
                                            plan,
                                            f,
                                            locator.getService(sid, fieldType));
                                        return;
                                    }
                                }

                                Named named = ap.getAnnotation(Named.class);

                                if (named == null) {
                                    addInjectPlan(
                                        plan,
                                        f,
                                        locator.getObject(fieldType, ap));
                                } else {
                                    addInjectPlan(
                                        plan,
                                        f,
                                        locator.getService(
                                            named.value(),
                                            fieldType));
                                }

                                return;
                            }

                            // Ignore fields that do not have the necessary annotation.
                        }
                    });
            }

            clazz = clazz.getSuperclass();
        }
    }

    private static <T> void addInjectPlan(
        ConstructionPlan<T> plan,
        final Field field,
        final Object injectedValue) {
        plan.add(
            new InitializationPlan<T>() {
                @Override
                public String getDescription() {
                    return String.format(
                        "Injecting %s into field %s of class %s.",
                        injectedValue,
                        field.getName(),
                        field.getDeclaringClass().getName());
                }

                @Override
                public void initialize(T instance) {
                    inject(instance, field, injectedValue);
                }
            });
    }

    private static boolean hasAnnotation(
        AccessibleObject member,
        Class<? extends Annotation> annotationType) {
        return member.getAnnotation(annotationType) != null;
    }

    private static <T> void extendPlanForPostInjectionMethods(
        ConstructionPlan<T> plan,
        OperationTracker tracker,
        ServiceLocator locator,
        InjectionResources resources,
        Class<T> instantiatedClass) {
        for (Method m : instantiatedClass.getMethods()) {
            if (hasAnnotation(m, PostInjection.class) ||
                hasAnnotation(m, PostConstruct.class)) {
                extendPlanForPostInjectionMethod(
                    plan,
                    tracker,
                    locator,
                    resources,
                    m);
            }
        }
    }

    private static void extendPlanForPostInjectionMethod(
        final ConstructionPlan<?> plan,
        final OperationTracker tracker,
        final ServiceLocator locator,
        final InjectionResources resources,
        final Method method) {
        tracker.run(
            "Computing parameters for post-injection method " + method,
            new Runnable() {
                @Override
                public void run() {
                    final ObjectCreator[] parameters = calculateParametersForMethod(
                        method,
                        locator,
                        resources,
                        tracker);

                    plan.add(
                        new InitializationPlan<Object>() {
                            @Override
                            public String getDescription() {
                                return "Invoking " + method;
                            }

                            private final java.lang.invoke.MethodHandle mh = MethodHandleUtils.methodHandle(method);

                            @Override
                            public void initialize(Object instance) {
                                Object[] realized = realizeObjects(parameters);

                                Object[] args = new Object[realized.length + 1];
                                args[0] = instance;
                                System.arraycopy(
                                    realized,
                                    0,
                                    args,
                                    1,
                                    realized.length);

                                try {
                                    mh.invokeWithArguments(args);
                                } catch (RuntimeException | Error e) {
                                    throw e;
                                } catch (Throwable t) {
                                    throw new RuntimeException(
                                        String.format(
                                            "Exception invoking method %s: %s",
                                            method,
                                            toMessage(t)),
                                        t);
                                }
                            }
                        });
                }
            });
    }

    public static <T> ObjectCreator<T> createMethodInvocationPlan(
        final OperationTracker tracker,
        final ServiceLocator locator,
        final InjectionResources resources,
        final Logger logger,
        final String description,
        final Object instance,
        final Method method) {
        return tracker.invoke("Creating plan to invoke " + method, () -> {
            ObjectCreator[] methodParameters = calculateParametersForMethod(
                method,
                locator,
                resources,
                tracker);

            var core = new MethodHandleInvoker<T>(instance, method, methodParameters);

            var wrapped = logger == null
                ? core
                : new LoggingInvokableWrapper<T>(logger, description, core);

            return new ConstructionPlan(tracker, description, wrapped);
        });
    }

    /**
     */
    public static final Function<ObjectCreator, Object> CREATE_OBJECT = ObjectCreator::create;

    /**
     */
    public static Object[] realizeObjects(ObjectCreator[] creators) {
        return Arrays.stream(creators)
            .map(CREATE_OBJECT)
            .toArray(Object[]::new);
    }

    /**
     * Invokes a constructor via its MethodHandle, realizing the constructor
     * parameters first.
     */
    @SuppressWarnings("unchecked")
    public static <T> T invokeConstructor(
        MethodHandle constructorHandle,
        ObjectCreator<?>[] constructorParameters) {
        Object[] realized = realizeObjects(constructorParameters);

        try {
            return (T) constructorHandle.invokeWithArguments(realized);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(
                String.format(
                    "Error invoking constructor via MethodHandle: %s",
                    toMessage(t)),
                t);
        }
    }

    /**
     * Extracts the string keys from a map and returns them in sorted order. The
     * keys are converted to strings.
     *
     * @param map
     *            the map to extract keys from (may be null)
     * @return the sorted keys, or the empty set if map is null
     */

    public static List<String> sortedKeys(Map<?, ?> map) {
        if (map == null)
            return Collections.emptyList();

        List<String> keys = new ArrayList<>();

        for (Object o : map.keySet())
            keys.add(String.valueOf(o));

        Collections.sort(keys);

        return keys;
    }

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

            if (upperCase && !postSpace)
                builder.append(' ');

            builder.append(ch);

            postSpace = upperCase;
        }

        return builder.toString();
    }

    /**
     * Used to convert a property expression into a key that can be used to locate
     * various resources (Blocks, messages, etc.). Strips out any punctuation
     * characters, leaving just words characters (letters, number and the
     * underscore).
     *
     * @param expression
     *            a property expression
     * @return the expression with punctuation removed
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
        String propertyExpression) {
        String key = id + "-label";
        String label = messages.apply(key);

        if (label != null)
            return label;

        return toUserPresentable(
            extractIdFromPropertyExpression(lastTerm(propertyExpression)));
    }

    public static String replace(
        String input,
        Pattern pattern,
        String replacement) {
        return pattern.matcher(input).replaceAll(replacement);
    }

    /**
     * Converts a class to a user presentable type name. Replacement for
     * {@code PlasticUtils.toTypeName(Class)}.
     *
     * @param type
     *            the type to convert
     * @return a user presentable type name
     */
    public static String toSimpleTypeName(Class<?> type) {
        if (type == null)
            return "null";
        if (type.isArray())
            return (toSimpleTypeName(type.getComponentType()) + "[]");
        var name = type.getCanonicalName();
        return name != null ? name : type.getName();
    }

    /**
     * Converts an array of classes into an array of user presentable type names.
     * Replacement for {@code PlasticUtils.toTypeNames(Class[])}.
     *
     * @param types
     *            the types to convert
     * @return an array of user presentable type names
     */
    public static String[] toSimpleTypeNames(Class<?>[] types) {
        String[] result = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            result[i] = toSimpleTypeName(types[i]);
        }
        return result;
    }

    /**
     * An {@link IdMatcher} that matches a service ID using a glob-style or regex
     * pattern.
     */
    public static final class IdMatcherImpl implements IdMatcher {

        private final GlobPatternMatcher matcher;

        public IdMatcherImpl(String pattern) {
            this.matcher = new GlobPatternMatcher(pattern);
        }

        @Override
        public boolean matches(String id) {
            return matcher.matches(id);
        }
    }

    /**
     * An {@link IdMatcher} that matches when any of its constituent matchers match
     * (logical OR).
     */
    public static final class OrIdMatcher implements IdMatcher {

        private final List<IdMatcher> matchers;

        public OrIdMatcher(List<IdMatcher> matchers) {
            this.matchers = new ArrayList<>(matchers);
        }

        @Override
        public boolean matches(String id) {
            for (IdMatcher matcher : matchers) {
                if (matcher.matches(id))
                    return true;
            }
            return false;
        }
    }
}
