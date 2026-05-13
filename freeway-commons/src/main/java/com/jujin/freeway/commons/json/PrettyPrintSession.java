package com.jujin.freeway.commons.json;

import java.io.PrintWriter;

/**
 * Used to pretty-print JSON content, with a customizable indentation.
 *
 */
class PrettyPrintSession implements JSONPrintSession {

    private final PrintWriter writer;

    private final String indentString;

    private int indentLevel;

    enum Position {
        MARGIN, INDENTED, CONTENT
    }

    ;

    private Position position = Position.MARGIN;

    /**
     * Defaults the indentation to be two spaces per indentation level.
     */
    public PrettyPrintSession(PrintWriter writer) {
        this(writer, "  ");
    }

    /**
     * @param writer
     *            to which content is printed
     * @param indentString
     *            string used for indentation (written N times, once per current
     *            indent level)
     */
    public PrettyPrintSession(PrintWriter writer, String indentString) {
        this.writer = writer;
        this.indentString = indentString;
    }

    @Override
    public JSONPrintSession indent() {
        indentLevel++;

        return this;
    }

    @Override
    public JSONPrintSession newline() {
        if (position != Position.MARGIN) {
            writer.write('\n');
            position = Position.MARGIN;
        }

        return this;
    }

    @Override
    public JSONPrintSession outdent() {
        indentLevel--;

        return this;
    }

    private void addIndentation() {
        if (position == Position.MARGIN) {
            for (int i = 0; i < indentLevel; i++)
                writer.print(indentString);

            position = Position.INDENTED;
        }
    }

    private void addSep() {
        if (position == Position.CONTENT) {
            writer.print(' ');
        }
    }

    private void prepareToPrint() {
        addIndentation();

        addSep();
    }

    @Override
    public JSONPrintSession print(String value) {
        prepareToPrint();

        writer.print(value);

        position = Position.CONTENT;

        return this;
    }

    @Override
    public JSONPrintSession printQuoted(String value) {
        return print(JSONUtils.quote(value));
    }

    @Override
    public JSONPrintSession printSymbol(char symbol) {
        addIndentation();

        if (symbol != ',')
            addSep();

        writer.print(symbol);

        return this;
    }

}
