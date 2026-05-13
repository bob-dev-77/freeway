package com.jujin.freeway.commons.json.exceptions;

public class JSONArrayIndexOutOfBoundsException extends JSONException {

    private static final long serialVersionUID = -53336156278974940L;

    private final int index;

    public JSONArrayIndexOutOfBoundsException(int index) {
        super("Index: " + index);
        this.index = index;
    }

    public int getIndex() {
        return this.index;
    }

}
