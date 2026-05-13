package com.jujin.freeway.ioc;

/**
 * Creates "interned" strings that are unique for the same content. This is used
 * for common description strings, particularly those used by
 * {@link com.jujin.freeway.Binding} instances. The internal cache of interned
 * strings id cleared whenever the
 * {@link com.jujin.freeway.ioc.InvalidationEventHub} is invalidated (i.e., when
 * component class files change).
 *
 */
public interface StringInterner {
    /**
     * Interns a string.
     *
     * @param string
     *            the string to intern
     * @return the input string, or another string instance with the same content
     */
    String intern(String string);

    /**
     * Formats a string (using {@link String#format(String, Object[])}) and returns
     * the interned result.
     *
     * @param format
     *            string format
     * @param arguments
     *            used inside the format
     * @return formatted and interned string
     */
    String format(String format, Object... arguments);
}
