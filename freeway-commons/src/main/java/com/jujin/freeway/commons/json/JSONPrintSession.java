package com.jujin.freeway.commons.json;

import java.io.PrintWriter;

/**
 * Encapsulates a {@link PrintWriter} and the rules for indentation and spacing.
 *
 */
interface JSONPrintSession {
    /**
     * Prints a value as is; the value is assumed to be a string representation of a
     * number of boolean and not require quotes. A space may be inserted before the
     * value.
     *
     * @param value
     *            unquoted value to print
     * @return the session (for fluent method invocations)
     */
    JSONPrintSession print(String value);

    /**
     * Prints a value enclosed in double quotes. Any internal quotes are escaped. A
     * space may be inserted before the value.
     *
     * @param value
     *            the string to be printed enclosed in quotes
     * @return the session (for fluent method invocations)
     */
    JSONPrintSession printQuoted(String value);

    /**
     * Begins a new line and the current indentation level.
     *
     * @return the session (for fluent method invocations)
     */
    JSONPrintSession newline();

    /**
     * Prints a symbol (i.e., ':', '{', '}', '[', ']', or ','). A space may be
     * inserted before the symbol.
     */

    JSONPrintSession printSymbol(char symbol);

    /**
     * Increments the indentation level.
     *
     * @return new session reflecting the indentation
     */
    JSONPrintSession indent();

    /**
     * Decrements the indentation level.
     *
     * @return new session reflecting the indentation
     */
    JSONPrintSession outdent();
}
