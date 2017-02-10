package com.genymobile.gnirehtet;

public class Binary {

    private Binary() {
        // not instantiable
    }

    public static int unsigned(byte value) {
        return value & 0xff;
    }

    public static int unsigned(short value) {
        return value & 0xffff;
    }

    public static long unsigned(int value) {
        return value & 0xffffffffl;
    }

    public static String toString(byte[] data, int len) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < len; ++i) {
            if (i % 8 == 0)
                builder.append('\n');
            builder.append(String.format("%02X ", data[i] & 0xff));
        }
        return builder.toString();
    }

}
