package com.genymobile.relay;

import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Log {

    enum Level {
        VERBOSE("V"),
        DEBUG("D"),
        INFO("I"),
        WARNING("W"),
        ERROR("E");

        private final String id;

        Level(String id) {
            this.id = id;
        }
    }

    private static Level threshold = Level.DEBUG;

    private static final DateFormat FORMAT = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss.SSS");
    private static final Date date = new Date();

    private Log() {
    }

    private static Level getThreshold() {
        return threshold;
    }

    private static void setThreshold(Level threshold) {
        Log.threshold = threshold;
    }

    public static boolean isEnabled(Level level) {
        return level.ordinal() >= threshold.ordinal();
    }

    public static boolean isVerboseEnabled() {
        return isEnabled(Level.VERBOSE);
    }

    public static boolean isDebugEnabled() {
        return isEnabled(Level.DEBUG);
    }

    public static boolean isInfoEnabled() {
        return isEnabled(Level.INFO);
    }

    public static boolean isWarningEnabled() {
        return isEnabled(Level.WARNING);
    }

    public static boolean isErrorEnabled() {
        return isEnabled(Level.ERROR);
    }

    private static String getDate() {
        date.setTime(System.currentTimeMillis());
        return FORMAT.format(date);
    }

    private static String format(Level level, String tag, String message) {
        return getDate() + " " + level.id + " " + tag + ": " + message;
    }

    private static void l(Level level, PrintStream stream, String tag, String message, Throwable e) {
        if (isEnabled(level)) {
            stream.println(format(level, tag, message));
            if (e != null) {
                e.printStackTrace();
            }
        }
    }

    public static void v(String tag, String message, Throwable e) {
        l(Level.VERBOSE, System.out, tag, message, e);
    }

    public static void v(String tag, String message) {
        v(tag, message, null);
    }

    public static void d(String tag, String message, Throwable e) {
        l(Level.DEBUG, System.out, tag, message, e);
    }

    public static void d(String tag, String message) {
        d(tag, message, null);
    }

    public static void i(String tag, String message, Throwable e) {
        l(Level.INFO, System.out, tag, message, e);
    }

    public static void i(String tag, String message) {
        i(tag, message, null);
    }

    public static void w(String tag, String message, Throwable e) {
        l(Level.WARNING, System.out, tag, message, e);
    }

    public static void w(String tag, String message) {
        w(tag, message, null);
    }

    public static void e(String tag, String message, Throwable e) {
        l(Level.ERROR, System.err, tag, message, e);
    }

    public static void e(String tag, String message) {
        e(tag, message, null);
    }
}
