package com.lushprojects.circuitjs1.client.io.sfcr;

public class ParseWarning {
    private final int line;
    private final String message;

    public ParseWarning(int line, String message) {
        this.line = line;
        this.message = message;
    }

    public int getLine() {
        return line;
    }

    public String getMessage() {
        return message;
    }
}
