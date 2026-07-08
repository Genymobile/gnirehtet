/*
 * Based on Gnirehtet.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.genymobile.gnirehtet;

import java.text.DecimalFormat;

public final class ByteFormatter {

    private static final DecimalFormat BYTE_FORMAT = new DecimalFormat("#0.0");

    private ByteFormatter() {
        // not instantiable
    }

    public static String format(long bytes) {
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            ++unit;
        }
        if (unit == 0) {
            return bytes + " B";
        }
        return BYTE_FORMAT.format(value) + " " + units[unit];
    }
}
