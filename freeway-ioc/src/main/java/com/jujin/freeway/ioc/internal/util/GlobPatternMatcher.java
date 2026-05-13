package com.jujin.freeway.ioc.internal.util;

import java.util.regex.Pattern;

/**
 * Used when matching identifiers. In the early days of T5 IoC, matching was
 * based on shell-style glob matches (a '*' could represent zero or more
 * characters). But that was limiting so now we check to see if the provided
 * pattern looks like a glob (just characters and asterisks, for compatibility
 * with older code) and, if not, we assume it is a regular expression.
 */
public class GlobPatternMatcher {
    private final Pattern pattern;

    private final static Pattern oldStyleGlob = Pattern.compile("[a-z\\*]+", Pattern.CASE_INSENSITIVE);

    public GlobPatternMatcher(String pattern) {
        this.pattern = compilePattern(pattern);
    }

    private static Pattern compilePattern(String pattern) {
        return Pattern.compile(createRegexpFromGlob(pattern), Pattern.CASE_INSENSITIVE);
    }

    private static String createRegexpFromGlob(String pattern) {
        return oldStyleGlob.matcher(pattern).matches()
            ? pattern.replace("*", ".*")
            : pattern;
    }

    public boolean matches(String input) {
        return pattern.matcher(input).matches();
    }
}
