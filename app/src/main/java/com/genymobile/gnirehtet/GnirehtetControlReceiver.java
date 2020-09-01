package com.genymobile.gnirehtet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class GnirehtetControlReceiver extends BroadcastReceiver {
    private static final String TAG = GnirehtetControlReceiver.class.getSimpleName();
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received request " + action);
        if ("android.hardware.usb.action.USB_STATE".equals(action)) {
            if (intent.getExtras().getBoolean("connected")) {
                // USB was connected
            } else {
                // USB was disconnected
                stopGnirehtet(context);
            }
        }
    }

    private void stopGnirehtet(Context context) {
        GnirehtetService.stop(context);
    }
}
