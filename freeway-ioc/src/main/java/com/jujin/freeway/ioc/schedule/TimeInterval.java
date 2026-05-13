package com.jujin.freeway.ioc.schedule;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Used to represent a period of time, specifically as a configuration value.
 * This is often used to specify timeouts.
 * <p>
 * TimePeriods are parsed from strings.
 * <p>
 * The string specifys a number of terms. The values of all the terms are summed
 * together to form the total time period. Each term consists of a number
 * followed by a unit. Units (from largest to smallest) are:
 * <dl>
 * <dt>y
 * <dd>year
 * <dt>d
 * <dd>day
 * <dt>h
 * <dd>hour
 * <dt>m
 * <dd>minute
 * <dt>s
 * <dd>second
 * <dt>ms
 * <dd>millisecond
 * </dl>
 * Example: "2 h 30 m". By convention, terms are specified largest to smallest.
 * A term without a unit is assumed to be milliseconds. Units are case
 * insensitive ("h" or "H" are treated the same).
 */
public class TimeInterval {

    private static final Map<String, Long> UNITS = new TreeMap<>(
        String.CASE_INSENSITIVE_ORDER);

    private static final long MILLISECOND = 1000l;

    static {
        UNITS.put("ms", 1l);
        UNITS.put("s", MILLISECOND);
        UNITS.put("m", 60 * MILLISECOND);
        UNITS.put("h", 60 * UNITS.get("m"));
        UNITS.put("d", 24 * UNITS.get("h"));
        UNITS.put("y", 365 * UNITS.get("d"));
    }

    /**
     * The unit keys, sorted in descending order.
     */
    private static final List<String> UNIT_KEYS = List.of(
        "y",
        "d",
        "h",
        "m",
        "s",
        "ms");

    private static final Pattern PATTERN = Pattern.compile(
        "\\s*(\\d+)\\s*([a-z]*)",
        Pattern.CASE_INSENSITIVE);

    private final long milliseconds;

    /**
     * Creates a TimeInterval for a string.
     *
     * @param input
     *            the string specifying the amount of time in the period
     */
    public TimeInterval(String input) {
        this(parseMilliseconds(input));
    }

    public TimeInterval(long milliseconds) {
        this.milliseconds = milliseconds;
    }

    public long milliseconds() {
        return milliseconds;
    }

    public long seconds() {
        return milliseconds / MILLISECOND;
    }

    /**
     * Converts the milliseconds back into a string (compatible with
     * {@link #TimeInterval(String)}).
     *
     */
    public String toDescription() {
        var builder = new StringBuilder();

        String sep = "";

        long remainder = milliseconds;

        for (String key : UNIT_KEYS) {
            if (remainder == 0)
                break;

            long value = UNITS.get(key);

            long units = remainder / value;

            if (units > 0) {
                builder.append(sep);
                builder.append(units);
                builder.append(key);

                sep = " ";

                remainder = remainder % value;
            }
        }

        return builder.toString();
    }

    static long parseMilliseconds(String input) {
        long milliseconds = 0l;

        Matcher matcher = PATTERN.matcher(input);

        matcher.useAnchoringBounds(true);

        // TODO: Notice non matching characters and reject input, including at end

        int lastMatchEnd = -1;

        while (matcher.find()) {
            int start = matcher.start();

            if (lastMatchEnd + 1 < start) {
                String invalid = input.substring(lastMatchEnd + 1, start);
                throw new RuntimeException(
                    String.format(
                        "Unexpected string '%s' (in time interval '%s').",
                        invalid,
                        input));
            }

            lastMatchEnd = matcher.end();

            long count = Long.parseLong(matcher.group(1));
            String units = matcher.group(2);

            if (units.length() == 0) {
                milliseconds += count;
                continue;
            }

            Long unitValue = UNITS.get(units);

            if (unitValue == null)
                throw new RuntimeException(
                    String.format(
                        "Unknown time interval unit '%s' (in '%s').  Defined units: %s.",
                        units,
                        input,
                        joinSorted(UNITS.keySet())));

            milliseconds += count * unitValue;
        }

        if (lastMatchEnd + 1 < input.length()) {
            String invalid = input.substring(lastMatchEnd + 1);
            throw new RuntimeException(
                String.format(
                    "Unexpected string '%s' (in time interval '%s').",
                    invalid,
                    input));
        }

        return milliseconds;
    }

    private static String joinSorted(Collection<?> elements) {
        if (elements == null || elements.isEmpty())
            return "(none)";

        List<String> list = new ArrayList<>();

        for (Object o : elements)
            list.add(String.valueOf(o));

        Collections.sort(list);

        return join(list);
    }

    private static String join(List<?> elements) {
        return join(elements, ", ");
    }

    private static String join(List<?> elements, String separator) {
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

    @Override
    public String toString() {
        return String.format("TimeInterval[%d ms]", milliseconds);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;

        if (obj instanceof TimeInterval) {
            TimeInterval tp = (TimeInterval) obj;

            return milliseconds == tp.milliseconds;
        }

        return false;
    }
}
