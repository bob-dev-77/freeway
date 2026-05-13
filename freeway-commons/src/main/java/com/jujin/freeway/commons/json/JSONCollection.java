package com.jujin.freeway.commons.json;

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.io.Serializable;

/**
 * Base class for {@link JSONArray} and {@link JSONObject} that exists to
 * organize the code for printing such objects (either compact or pretty).
 *
 */
public abstract class JSONCollection implements Serializable {
    /**
     * Converts this JSON collection into a parsable string representation.
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     * <p>
     * The result will be pretty printed for readability.
     *
     * @return a printable, displayable, portable, transmittable representation of
     *         the object, beginning with <code>{</code>&nbsp;<small>(left
     *         brace)</small> and ending with <code>}</code>&nbsp;<small>(right
     *         brace)</small>.
     */
    @Override
    public String toString() {
        CharArrayWriter caw = new CharArrayWriter();
        PrintWriter pw = new PrintWriter(caw);

        JSONPrintSession session = new PrettyPrintSession(pw);

        print(session);

        pw.close();

        return caw.toString();
    }

    /**
     * Converts the JSONObject to a compact or pretty-print string representation
     *
     * @param compact
     *            if true, return minimal format string.
     */
    public String toString(boolean compact) {
        return compact ? toCompactString() : toString();
    }

    /**
     * Prints the JSONObject as a compact string (no extra punctuation).
     */
    public String toCompactString() {
        StringBuilder buf = new StringBuilder();
        print(new CompactSession(buf));
        return buf.toString();
    }

    /**
     * Prints the JSONObject to the write (compactly or not).
     *
     * @param writer
     *            to write content to
     * @param compact
     *            if true, then write compactly, if false, write with pretty
     *            printing
     */
    public void print(PrintWriter writer, boolean compact) {
        JSONPrintSession session = compact ? new CompactSession(writer) : new PrettyPrintSession(writer);

        print(session);
    }

    /**
     * Prints the JSONObject to the writer compactly (with no extra whitespace).
     */
    public void print(PrintWriter writer) {
        print(new CompactSession(writer));
    }

    /**
     * Prints the JSONObject to the writer using indentation (two spaces per
     * indentation level).
     */
    public void prettyPrint(PrintWriter writer) {
        print(writer, false);
    }

    /**
     * Print the collection in a parsable format using the session to (optionally)
     * inject extra whitespace (for "pretty printing").
     */
    abstract void print(JSONPrintSession session);
}
