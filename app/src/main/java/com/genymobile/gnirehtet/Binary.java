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

}
