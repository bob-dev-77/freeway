package com.jujin.freeway.ioc.internal.util;

import java.util.*;

/**
 * Collection, array, and map utility methods.
 */
public class CollectionUtils {

    public static int size(Object[] array) {
        return array == null ? 0 : array.length;
    }

    public static int size(Collection collection) {
        return collection == null ? 0 : collection.size();
    }

    public static List<String> toList(Enumeration e) {
        List<String> result = new ArrayList<>();
        while (e.hasMoreElements()) result.add((String) e.nextElement());
        Collections.sort(result);
        return result;
    }

    public static <K, V> Set<K> keys(Map<K, V> map) {
        return map == null ? Collections.emptySet() : map.keySet();
    }

    public static <K, V> V get(Map<K, V> map, K key) {
        return map == null ? null : map.get(key);
    }

    public static List<String> sortedKeys(Map<?, ?> map) {
        if (map == null) return Collections.emptyList();
        List<String> keys = new ArrayList<>();
        for (Object o : map.keySet()) keys.add(String.valueOf(o));
        Collections.sort(keys);
        return keys;
    }

    public static <K, V> void addToMapList(Map<K, List<V>> map, K key, V value) {
        List<V> list = map.get(key);
        if (list == null) {
            list = new ArrayList<>();
            map.put(key, list);
        }
        list.add(value);
    }

    public static boolean isEmptyCollection(Object input) {
        return input instanceof Collection && ((Collection) input).isEmpty();
    }

    public static <T> Iterator<T> reverseIterator(final List<T> list) {
        final var normal = list.listIterator(list.size());
        return new Iterator<T>() {
            @Override public boolean hasNext() { return normal.hasPrevious(); }
            @Override public T next() { return normal.previous(); }
            @Override public void remove() { throw new UnsupportedOperationException(); }
        };
    }

    private CollectionUtils() {}
}
