package com.jujin.freeway.ioc.internal.util;

import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * String formatting, display, and type-name utilities.
 */
public class DisplayUtils {

    private static final Pattern NON_WORD_PATTERN = Pattern.compile("\\W");
    private static final Pattern NAME_PATTERN = Pattern.compile(
            "^[_|$]*([\\p{javaJavaIdentifierPart}]+?)[_|$]*$",
            Pattern.CASE_INSENSITIVE);

    /**
     * Converts a method to a user presentable string.
     */
    public static String asString(Method method) {
        var buf = new StringBuilder();
        buf.append(method.getDeclaringClass().getName());
        buf.append('.');
        buf.append(method.getName());
        buf.append('(');
        var types = method.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) buf.append(", ");
            buf.append(types[i].getSimpleName());
        }
        return buf.append(')').toString();
    }

    public static String toSimpleTypeName(Class<?> type) {
        if (type == null) return "null";
        if (type.isArray()) return toSimpleTypeName(type.getComponentType()) + "[]";
        var name = type.getCanonicalName();
        return name != null ? name : type.getName();
    }

    public static String[] toSimpleTypeNames(Class<?>[] types) {
        String[] r = new String[types.length];
        for (int i = 0; i < types.length; i++) r[i] = toSimpleTypeName(types[i]);
        return r;
    }

    public static String stripMemberName(String memberName) {
        Matcher m = NAME_PATTERN.matcher(memberName);
        if (!m.matches()) throw new IllegalArgumentException(
                String.format("Input '%s' is not a valid Java identifier.", memberName));
        return m.group(1);
    }

    public static String join(List<?> elements) {
        return join(elements, ", ");
    }

    public static String join(List<?> elements, String separator) {
        switch (elements.size()) {
            case 0: return "";
            case 1: return String.valueOf(elements.get(0));
            default:
                var buf = new StringBuilder();
                boolean first = true;
                for (Object o : elements) {
                    if (!first) buf.append(separator);
                    String s = String.valueOf(o);
                    if (s.equals("")) s = "(blank)";
                    buf.append(s);
                    first = false;
                }
                return buf.toString();
        }
    }

    public static String joinSorted(Collection<?> elements) {
        if (elements == null || elements.isEmpty()) return "(none)";
        List<String> list = new ArrayList<>();
        for (Object o : elements) list.add(String.valueOf(o));
        Collections.sort(list);
        return join(list);
    }

    public static boolean isBlank(String input) {
        return input == null || input.isBlank();
    }

    public static boolean isNonBlank(String input) {
        return input != null && !input.isBlank();
    }

    public static String capitalize(String input) {
        if (input.isEmpty()) return input;
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    public static boolean containsSymbols(String input) {
        return input.contains("${");
    }

    public static String lastTerm(String input) {
        int dotx = input.lastIndexOf('.');
        return dotx < 0 ? input : input.substring(dotx + 1);
    }

    public static String toUserPresentable(String id) {
        var buf = new StringBuilder(id.length() * 2);
        char[] chars = id.toCharArray();
        boolean postSpace = true;
        boolean upcaseNext = true;
        for (char ch : chars) {
            if (upcaseNext) {
                buf.append(Character.toUpperCase(ch));
                upcaseNext = false;
                continue;
            }
            if (ch == '_') {
                buf.append(' ');
                upcaseNext = true;
                continue;
            }
            boolean upper = Character.isUpperCase(ch);
            if (upper && !postSpace) buf.append(' ');
            buf.append(ch);
            postSpace = upper;
        }
        return buf.toString();
    }

    public static String extractIdFromPropertyExpression(String expression) {
        return replace(expression, NON_WORD_PATTERN, "");
    }

    public static String defaultLabel(
            String id,
            java.util.function.Function<String, String> messages,
            String propertyExpression) {
        String key = id + "-label";
        String label = messages.apply(key);
        if (label != null) return label;
        return toUserPresentable(extractIdFromPropertyExpression(lastTerm(propertyExpression)));
    }

    public static String replace(String input, Pattern pattern, String replacement) {
        return pattern.matcher(input).replaceAll(replacement);
    }

    private DisplayUtils() {}
}
