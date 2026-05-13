package com.jujin.freeway.commons.json;

/**
 * An interface that allows an object to be stored as a {@link JSONObject} or
 * {@link JSONArray} value. When printed, the value of {@link #toJSONString()}
 * is printed without quotes or other substitution; it is the responsibility of
 * the object to provide proper JSON output.
 */
public interface JSONString {
    /**
     * The <code>toJSONString</code> method allows a class to produce its own JSON
     * serialization.
     *
     * @return A strictly syntactically correct JSON text.
     */
    public String toJSONString();
}
