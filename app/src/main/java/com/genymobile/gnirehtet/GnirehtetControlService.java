package com.genymobile.gnirehtet;

import android.app.Service;
import android.content.Intent;
import android.net.VpnService;
import android.os.IBinder;
import android.util.Log;

import static com.genymobile.gnirehtet.AuthorizationActivity.EXTRA_VPN_CONFIGURATION;

/**
 * Service to expose {@link #ACTION_GNIREHTET_START START} and {@link #ACTION_GNIREHTET_STOP}
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
 * GnirehtetControlService} starts an {@link android.app.Activity Activity} only when necessary.
 * <p>
 * Stopping the VPN just consists in delegating the request to {@link GnirehtetService} (which is
 * accessible from here).
 */
public class GnirehtetControlService extends Service {

    public static final String ACTION_GNIREHTET_START = "com.genymobile.gnirehtet.START";
    public static final String ACTION_GNIREHTET_STOP = "com.genymobile.gnirehtet.STOP";

    public static final String EXTRA_DNS_SERVERS = "dnsServers";

    private static final String TAG = GnirehtetControlService.class.getSimpleName();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (ACTION_GNIREHTET_START.equals(action)) {
            VpnConfiguration config = createConfig(intent);
            startGnirehtet(config);
        } else if (ACTION_GNIREHTET_STOP.equals(action)) {
            stopGnirehtet();
        }
        stopSelf();
        return START_NOT_STICKY;
    }

    private static VpnConfiguration createConfig(Intent intent) {
        String[] dnsServers = intent.getStringArrayExtra(EXTRA_DNS_SERVERS);
        if (dnsServers == null) {
            dnsServers = new String[0];
        }
        return new VpnConfiguration(Net.toInetAddresses(dnsServers));
    }

    private void startGnirehtet(VpnConfiguration config) {
        Log.d(TAG, "Received request " + ACTION_GNIREHTET_START);
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent == null) {
            Log.d(TAG, "VPN was already authorized");
            // we got the permission, start the service now
            GnirehtetService.start(this, config);
        } else {
            Log.d(TAG, "VPN requires the authorization from the user, requesting...");
            requestAuthorization(vpnIntent, config);
        }
    }

    private void stopGnirehtet() {
        GnirehtetService.stop(this);
    }

    private void requestAuthorization(Intent vpnIntent, VpnConfiguration config) {
        // we must send the intent with startActivityForResult, so we need to send it from an activity
        Intent intent = new Intent(this, AuthorizationActivity.class);
        intent.putExtra(AuthorizationActivity.EXTRA_VPN_INTENT, vpnIntent);
        intent.putExtra(EXTRA_VPN_CONFIGURATION, config);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
