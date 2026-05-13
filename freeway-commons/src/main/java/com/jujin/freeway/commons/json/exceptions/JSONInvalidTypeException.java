package com.jujin.freeway.commons.json.exceptions;

public class JSONInvalidTypeException extends JSONException {

    private static final long serialVersionUID = 934805933638996600L;

    private Class<? extends Object> invalidClass;

    public JSONInvalidTypeException(Class<? extends Object> invalidClass) {
        super(
            "JSONArray values / JSONObject properties may be one of Boolean, Number, String, com.jujin.freeway.commons.json.JSONArray, com.jujin.freeway.commons.json.JSONLiteral, com.jujin.freeway.commons.json.JSONObject, com.jujin.freeway.commons.json.JSONObject$Null, com.jujin.freeway.commons.json.JSONString. Type "
                + invalidClass.getName() + " is not allowed.");

        this.invalidClass = invalidClass;
    }

    public Class<? extends Object> getInvalidClass() {
        return this.invalidClass;
    }
}
