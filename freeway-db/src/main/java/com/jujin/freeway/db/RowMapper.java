package com.jujin.freeway.db;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps a row of a {@link ResultSet} to an object. Prefer the built-in
 * record/bean mapper via {@link Query#list(Class)} and friends; implement
 * this interface only for custom mapping logic.
 */
@FunctionalInterface
public interface RowMapper<T> {

    T map(ResultSet rs, int rowNum) throws SQLException;
}
