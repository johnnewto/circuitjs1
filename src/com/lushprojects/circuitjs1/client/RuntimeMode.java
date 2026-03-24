package com.lushprojects.circuitjs1.client;


public final class RuntimeMode {
    private static boolean nonInteractiveRuntime;

    private RuntimeMode() {
    }

    public static void setNonInteractiveRuntime(boolean value) {
        nonInteractiveRuntime = value;
    }

    public static boolean isNonInteractiveRuntime() {
        return nonInteractiveRuntime;
    }

    public static boolean isGwt() {
        return !nonInteractiveRuntime;
    }
}