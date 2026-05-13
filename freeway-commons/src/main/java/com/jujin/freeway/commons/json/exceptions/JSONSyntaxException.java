package com.jujin.freeway.commons.json.exceptions;

public class JSONSyntaxException extends JSONException {

    private static final long serialVersionUID = 5647885303727734937L;

    private final int position;

    public JSONSyntaxException(int position, String message) {
        super(message);
        this.position = position;
    }

    public int getPosition() {
        return this.position;
    }

}
