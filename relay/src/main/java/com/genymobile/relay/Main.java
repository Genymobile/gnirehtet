package com.genymobile.relay;

import java.io.IOException;

public class Main {
    private static final String TAG = Main.class.getSimpleName();

    public static void main(String... args) throws IOException {
        Log.i(TAG, "Starting server...");
        new Relay().start();
    }
}
