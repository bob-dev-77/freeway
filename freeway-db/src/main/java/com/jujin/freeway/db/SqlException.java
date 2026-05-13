package com.jujin.freeway.db;

/**
 * Unchecked wrapper around JDBC {@link java.sql.SQLException}. All database
 * operations throw this rather than forcing callers to handle checked
 * exceptions.
 */
public class SqlException extends RuntimeException {

    public SqlException(String message) {
        super(message);
    }

    public SqlException(String message, Throwable cause) {
        super(message, cause);
    }

    public SqlException(Throwable cause) {
        super(cause);
    }
}
