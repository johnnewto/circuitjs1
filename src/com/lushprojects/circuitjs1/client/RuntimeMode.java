package com.lushprojects.circuitjs1.client;

public final class RuntimeMode {
    private static boolean headless;

    private RuntimeMode() {
    }

    public static void setHeadless(boolean value) {
        headless = value;
    }

    public static boolean isHeadless() {
        return headless;
    }

    public static boolean isGwt() {
        return !headless;
    }
}