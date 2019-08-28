/*
 * Copyright (C) 2017 Genymobile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.genymobile.gnirehtet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.util.Log;

import static com.genymobile.gnirehtet.AuthorizationActivity.EXTRA_VPN_CONFIGURATION;

/**
 * Receiver to expose {@link #ACTION_GNIREHTET_START START} and {@link #ACTION_GNIREHTET_STOP}
 * actions.
 * <p>
 * Since {@link GnirehtetService} extends {@link VpnService}, it requires the clients to have the
 * system permission {@code android.permission.BIND_VPN_SERVICE}, which {@code shell} have not. As a
 * consequence, we cannot expose our own actions intended to be called from {@code shell} directly
 * in {@link GnirehtetService}.
 * <p>
 * Starting the VPN requires authorization from the user. If the authorization is not granted yet,
 * an {@code Intent}, returned by the system, must be sent <strong>from an Activity</strong>
 * (through {@link android.app.Activity#startActivityForResult(Intent, int)
 * startActivityForResult()}. However, if the authorization is already granted, it is better to
 * avoid starting an {@link android.app.Activity Activity} (which would be useless), since it may
 * cause (minor) side effects (like closing any open virtual keyboard). Therefore, this {@link
 * GnirehtetControlReceiver} starts an {@link android.app.Activity Activity} only when necessary.
 * <p>
 * Stopping the VPN just consists in delegating the request to {@link GnirehtetService} (which is
 * accessible from here).
 */
public class GnirehtetControlReceiver extends BroadcastReceiver {

    public static final String ACTION_GNIREHTET_START = "com.genymobile.gnirehtet.START";
    public static final String ACTION_GNIREHTET_STOP = "com.genymobile.gnirehtet.STOP";

    public static final String EXTRA_DNS_SERVERS = "dnsServers";
    public static final String EXTRA_ROUTES = "routes";

    private static final String TAG = GnirehtetControlReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received request " + action);
        if (ACTION_GNIREHTET_START.equals(action)) {
            VpnConfiguration config = createConfig(intent);
            startGnirehtet(context, config);
        } else if (ACTION_GNIREHTET_STOP.equals(action)) {
            stopGnirehtet(context);
        }
    }

    public static VpnConfiguration createConfig(Intent intent) {
        String[] dnsServers = intent.getStringArrayExtra(EXTRA_DNS_SERVERS);
        if (dnsServers == null) {
            dnsServers = new String[0];
        }
        String[] routes = intent.getStringArrayExtra(EXTRA_ROUTES);
        if (routes == null) {
            routes = new String[0];
        }
        return new VpnConfiguration(Net.toInetAddresses(dnsServers), Net.toCIDRs(routes));
    }

    private void startGnirehtet(Context context, VpnConfiguration config) {
        Intent vpnIntent = VpnService.prepare(context);
        if (vpnIntent == null) {
            Log.d(TAG, "VPN was already authorized");
            // we got the permission, start the service now
            GnirehtetService.start(context, config);
        } else {
            Log.w(TAG, "VPN requires the authorization from the user, requesting...");
            requestAuthorization(context, vpnIntent, config);
        }
    }

    private void stopGnirehtet(Context context) {
        GnirehtetService.stop(context);
    }

    private void requestAuthorization(Context context, Intent vpnIntent, VpnConfiguration config) {
        // we must send the intent with startActivityForResult, so we need to send it from an activity
        Intent intent = new Intent(context, AuthorizationActivity.class);
        intent.putExtra(AuthorizationActivity.EXTRA_VPN_INTENT, vpnIntent);
        intent.putExtra(EXTRA_VPN_CONFIGURATION, config);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
