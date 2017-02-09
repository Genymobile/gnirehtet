package com.genymobile.relay;

public class Log {

    private Log() {
    }

    public static void d(String tag, String message) {
        System.out.println("[" + tag + "] " + message);
    }

    public static void e(String tag, String message, Throwable e) {
        System.err.println("[" + tag + "] " + message);
        e.printStackTrace();
    }

    public static void e(String tag, String message) {
        System.err.println("[" + tag + "] " + message);
    }
}
