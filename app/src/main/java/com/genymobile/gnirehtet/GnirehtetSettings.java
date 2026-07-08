/*
 * Based on Gnirehtet.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.genymobile.gnirehtet;

import android.content.Context;
import android.content.SharedPreferences;

final class GnirehtetSettings {

    static final String MODE_OFF = "off";
    static final String MODE_ON = "on";
    static final String MODE_AUTO = "auto";

    private static final String PREFERENCES_NAME = "gnirehtet";
    private static final String KEY_MODE = "mode";
    private static final String KEY_VPN_RUNNING = "vpnRunning";
    private static final String KEY_RELAY_CONNECTED = "relayConnected";
    private static final String KEY_LAST_STATUS = "lastStatus";
    private static final String KEY_PC_NAME = "pcName";

    private GnirehtetSettings() {
        // not instantiable
    }

    static String getMode(Context context) {
        return getPreferences(context).getString(KEY_MODE, MODE_AUTO);
    }

    static void setMode(Context context, String mode) {
        getPreferences(context).edit().putString(KEY_MODE, mode).apply();
    }

    static boolean acceptsConnection(Context context) {
        return !MODE_OFF.equals(getMode(context));
    }

    static void setVpnRunning(Context context, boolean running) {
        getPreferences(context).edit().putBoolean(KEY_VPN_RUNNING, running).apply();
    }

    static boolean isVpnRunning(Context context) {
        return getPreferences(context).getBoolean(KEY_VPN_RUNNING, false);
    }

    static void setRelayConnected(Context context, boolean connected) {
        getPreferences(context).edit().putBoolean(KEY_RELAY_CONNECTED, connected).apply();
    }

    static boolean isRelayConnected(Context context) {
        return getPreferences(context).getBoolean(KEY_RELAY_CONNECTED, false);
    }

    static void setLastStatus(Context context, String status) {
        getPreferences(context).edit().putString(KEY_LAST_STATUS, status).apply();
    }

    static String getLastStatus(Context context) {
        return getPreferences(context).getString(KEY_LAST_STATUS, "");
    }

    static void setPcName(Context context, String pcName) {
        getPreferences(context).edit().putString(KEY_PC_NAME, pcName).apply();
    }

    static String getPcName(Context context) {
        return getPreferences(context).getString(KEY_PC_NAME, "");
    }

    private static SharedPreferences getPreferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
