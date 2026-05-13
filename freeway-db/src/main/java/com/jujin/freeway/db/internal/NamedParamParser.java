package com.jujin.freeway.db.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parses SQL with {@code #name} or {@code :name} style parameters, converting
 * them to JDBC {@code ?} placeholders while preserving parameter order.
 */
class NamedParamParser {

    private static final Pattern NAMED = Pattern.compile("[:#]([a-zA-Z_][a-zA-Z0-9_]*)");

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
        var matcher = NAMED.matcher(sql);
        var sb = new StringBuilder();

        while (matcher.find()) {
            names.add(matcher.group(1)); // the name without : or # prefix
            matcher.appendReplacement(sb, "?");
        }
        matcher.appendTail(sb);

        return new Result(names, sb.toString());
    }
}
