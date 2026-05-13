package com.jujin.freeway.commons.json;

import java.io.IOException;

/**
 * Prints JSON content compactly, with no indentation or extra whitespace.
 */
class CompactSession implements JSONPrintSession {
    private final Appendable out;

    CompactSession(Appendable out) {
        this.out = out;
    }

    @Override
    public JSONPrintSession indent() {
        return this;
    }

    @Override
    public JSONPrintSession newline() {
        return this;
    }

    @Override
    public JSONPrintSession outdent() {
        return this;
    }

    @Override
    public JSONPrintSession print(String value) {
        try {
            out.append(value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public JSONPrintSession printQuoted(String value) {
        return print(JSONUtils.quote(value));
    }

    @Override
    public JSONPrintSession printSymbol(char symbol) {
        try {
            out.append(symbol);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }
}
