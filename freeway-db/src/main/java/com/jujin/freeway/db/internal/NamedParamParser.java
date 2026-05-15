package com.jujin.freeway.db.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses SQL with {@code #name} or {@code :name} style parameters, converting
 * them to JDBC {@code ?} placeholders while preserving parameter order.
 *
 * <p>
 * Uses a character-by-character scanner that tracks quote and comment state,
 * so parameter markers inside string literals or comments are correctly ignored.
 */
class NamedParamParser {

    private NamedParamParser() {}

    /**
     * Parsed SQL ready for {@link java.sql.PreparedStatement}.
     *
     * @param names ordered parameter names as they appear in the SQL
     * @param jdbcSql the SQL with named params replaced by {@code ?}
     */
    record Result(List<String> names, String jdbcSql) {}

    static Result parse(String sql) {
        var names = new ArrayList<String>();
        var sb = new StringBuilder(sql.length());
        int len = sql.length();
        int i = 0;

        while (i < len) {
            char c = sql.charAt(i);

            // Single-quoted string literal
            if (c == '\'') {
                sb.append(c);
                i++;
                while (i < len) {
                    char sc = sql.charAt(i);
                    sb.append(sc);
                    i++;
                    if (sc == '\'') {
                        // Check for escaped quote ('')
                        if (i < len && sql.charAt(i) == '\'') {
                            sb.append('\'');
                            i++;
                        } else {
                            break;
                        }
                    }
                }
                continue;
            }

            // Double-quoted identifier
            if (c == '"') {
                sb.append(c);
                i++;
                while (i < len && sql.charAt(i) != '"') {
                    sb.append(sql.charAt(i));
                    i++;
                }
                if (i < len) {
                    sb.append('"');
                    i++;
                }
                continue;
            }

            // Line comment (--)
            if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
                sb.append(c);
                i++;
                sb.append('-');
                i++;
                while (i < len && sql.charAt(i) != '\n') {
                    sb.append(sql.charAt(i));
                    i++;
                }
                continue;
            }

            // Block comment (/* ... */)
            if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
                sb.append('/');
                i++;
                sb.append('*');
                i++;
                while (i < len) {
                    char bc = sql.charAt(i);
                    sb.append(bc);
                    i++;
                    if (bc == '*' && i < len && sql.charAt(i) == '/') {
                        sb.append('/');
                        i++;
                        break;
                    }
                }
                continue;
            }

            // Named parameter (:name or #name)
            if ((c == ':' || c == '#') && i + 1 < len) {
                char next = sql.charAt(i + 1);
                if (isValidParamStart(next)) {
                    // Extract parameter name
                    int start = i + 1;
                    i += 2;
                    while (i < len && isValidParamChar(sql.charAt(i))) {
                        i++;
                    }
                    String paramName = sql.substring(start, i);
                    names.add(paramName);
                    sb.append('?');
                    continue;
                }
            }

            // Regular character
            sb.append(c);
            i++;
        }

        return new Result(names, sb.toString());
    }

    private static boolean isValidParamStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isValidParamChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
