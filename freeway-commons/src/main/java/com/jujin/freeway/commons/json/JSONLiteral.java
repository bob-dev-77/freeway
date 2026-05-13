package com.jujin.freeway.commons.json;

import java.io.Serializable;

/**
 * A way of including some text (often, text that violates the normal JSON
 * specification) as part of a JSON object or array. This is used in a few
 * places where data is nominally JSON but actually includes some non-conformant
 * elements, such as an inline function definition.
 *
 */
public class JSONLiteral implements JSONString, Serializable {
    private final String text;

    public JSONLiteral(String text) {
        this.text = text;
    }

    /**
     * Returns the text property; this is also the value placed into the JSON string
     * (unquoted, exactly as is).
     *
     * @return the text
     */
    @Override
    public String toString() {
        return text;
    }

    @Override
    public String toJSONString() {
        return text;
    }
}
