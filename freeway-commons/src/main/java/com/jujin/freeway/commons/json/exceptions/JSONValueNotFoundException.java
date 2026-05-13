package com.jujin.freeway.commons.json.exceptions;

import com.jujin.freeway.commons.json.JSONType;

public class JSONValueNotFoundException extends JSONException {

    private static final long serialVersionUID = -8709125433506778675L;

    private final String location;
    private final JSONType requiredType;

    public JSONValueNotFoundException(String location, JSONType requiredType) {
        super(location + " is not found. Required: " + requiredType.name());
        this.location = location;
        this.requiredType = requiredType;
    }

    public String getLocation() {
        return this.location;
    }

    public JSONType getRequiredType() {
        return this.requiredType;
    }

}
