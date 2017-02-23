package com.genymobile.relay;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Log {

    private static final DateFormat FORMAT = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss.S");
    private static final Date date = new Date();

    private Log() {
    }

    private static String getDate() {
        date.setTime(System.currentTimeMillis());
        return FORMAT.format(date);
    }

    public static void d(String tag, String message) {
        System.out.println(getDate() + " [" + tag + "] " + message);
    }

    public static void e(String tag, String message, Throwable e) {
        System.err.println(getDate() + " [" + tag + "] " + message);
        e.printStackTrace();
    }

    public static void e(String tag, String message) {
        System.err.println(getDate() + " [" + tag + "] " + message);
    }
}
