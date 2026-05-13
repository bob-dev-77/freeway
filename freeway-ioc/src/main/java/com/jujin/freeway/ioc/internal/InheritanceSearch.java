package com.jujin.freeway.ioc.internal;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

/**
 * Used to search from a particular class up the inheritance hierarchy of
 * extended classes and implemented interfaces.
 * <p>
 * The search starts with the initial class (provided in the constructor). It
 * progresses up the inheritance chain, but skips java.lang.Object.
 * <p>
 * Once classes are exhausted, the inheritance hierarchy is searched. This is a
 * breadth-first search, rooted in the interfaces implemented by the initial
 * class at its super classes.
 * <p>
 * Once all interfaces are exhausted, java.lang.Object is returned (it is always
 * returned last).
 * <p>
 * Two minor tweak to normal inheritance rules:
 * <ul>
 * <li>Normally, the parent class of an <em>object</em> array is
 * java.lang.Object, which is odd because FooService[] is assignable to
 * Object[]. Thus, we tweak the search so that the effective super class of
 * FooService[] is Object[].
 * <li>The "super class" of a primtive type is its <em>wrapper type</em>, with
 * the exception of void, whose "super class" is left at its normal value
 * (Object.class)
 * </ul>
 * <p>
 * This class implements the {@link Iterable} interface, so it can be used
 * directly in a for loop: <code> for (Class
 * search : new InheritanceSearch(startClass)) { ... } </code>
 * <p>
 * This class is not thread-safe.
 */
public class InheritanceSearch

    implements Iterator<Class<?>>, Iterable<Class<?>> {

    private Class<?> searchClass;

    private final Set<Class<?>> addedInterfaces = new HashSet<>();

    private final LinkedList<Class<?>> interfaceQueue = new LinkedList<>();

    private enum State {
        CLASS, INTERFACE, DONE,
    }

    private State state;

    public InheritanceSearch(Class<?> searchClass) {
        this.searchClass = searchClass;

        queueInterfaces(searchClass);

        state = searchClass == Object.class ? State.INTERFACE : State.CLASS;
    }

    private void queueInterfaces(Class<?> searchClass) {
        for (Class<?> intf : searchClass.getInterfaces()) {
            if (addedInterfaces.contains(intf))
                continue;

            interfaceQueue.addLast(intf);
            addedInterfaces.add(intf);
        }
    }

    @Override
    public Iterator<Class<?>> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        return state != State.DONE;
    }

    @Override
    public Class<?> next() {
        switch (state) {
            case CLASS:
                Class<?> result = searchClass;

                searchClass = parentOf(searchClass);

                if (searchClass == null)
                    state = State.INTERFACE;
                else
                    queueInterfaces(searchClass);

                return result;
            case INTERFACE:
                if (interfaceQueue.isEmpty()) {
                    state = State.DONE;
                    return Object.class;
                }

                Class<?> intf = interfaceQueue.removeFirst();

                queueInterfaces(intf);

                return intf;
            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Returns the parent of the given class. Tweaks inheritance for object arrays.
     * Returns null instead of Object.class.
     */
    private Class<?> parentOf(Class<?> clazz) {
        if (clazz != void.class && clazz.isPrimitive())
            return toWrapperType(
                clazz);

        if (clazz.isArray() && clazz != Object[].class) {
            Class<?> componentType = clazz.getComponentType();

            while (componentType.isArray())
                componentType = componentType.getComponentType();

            if (!componentType.isPrimitive())
                return Object[].class;
        }

        Class<?> parent = clazz.getSuperclass();

        return parent != Object.class ? parent : null;
    }

    private static Class<?> toWrapperType(Class<?> clazz) {
        if (clazz == int.class)
            return Integer.class;
        if (clazz == long.class)
            return Long.class;
        if (clazz == double.class)
            return Double.class;
        if (clazz == float.class)
            return Float.class;
        if (clazz == boolean.class)
            return Boolean.class;
        if (clazz == char.class)
            return Character.class;
        if (clazz == short.class)
            return Short.class;
        if (clazz == byte.class)
            return Byte.class;
        return clazz;
    }

    /**
     * @throws UnsupportedOperationException
     *             always
     */
    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
