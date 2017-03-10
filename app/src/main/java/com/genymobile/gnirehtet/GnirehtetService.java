package com.genymobile.gnirehtet;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

public class GnirehtetService extends VpnService {

    public static final boolean VERBOSE = false;

    private static final String ACTION_START_VPN = "com.genymobile.gnirehtet.START_VPN";
    private static final String ACTION_CLOSE_VPN = "com.genymobile.gnirehtet.CLOSE_VPN";
    private static final String EXTRA_VPN_CONFIGURATION = "vpnConfiguration";

    private static final String TAG = GnirehtetService.class.getName();

    private static final InetAddress VPN_ADDRESS = Net.toInetAddress(new byte[] {10, 0, 0, 2});
    private static final InetAddress VPN_ROUTE = Net.toInetAddress(new byte[] {0, 0, 0, 0}); // intercept everything

    private ParcelFileDescriptor vpnInterface = null;
    private Forwarder forwarder;

    public static void start(Context context, VpnConfiguration config) {
        Intent intent = new Intent(context, GnirehtetService.class);
        intent.setAction(ACTION_START_VPN);
        intent.putExtra(GnirehtetService.EXTRA_VPN_CONFIGURATION, config);
        context.startService(intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, GnirehtetService.class);
        intent.setAction(ACTION_CLOSE_VPN);
        context.startService(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        Log.d(TAG, "Received request " + action);
        if (ACTION_START_VPN.equals(action)) {
            if (isRunning()) {
                Log.d(TAG, "VPN already running, ignore START request");
            } else {
                VpnConfiguration config = intent.getParcelableExtra(EXTRA_VPN_CONFIGURATION);
                if (config == null) {
                    config = new VpnConfiguration();
                }
                startVpn(config);
            }
        } else if (ACTION_CLOSE_VPN.equals(action)) {
            close();
        }
        return START_NOT_STICKY;
    }

    private boolean isRunning() {
        return vpnInterface != null;
    }

    private void startVpn(VpnConfiguration config) {
        setupVpn(config);
        startForwarding();
    }

    private void setupVpn(VpnConfiguration config) {
        Builder builder = new Builder();
        builder.addAddress(VPN_ADDRESS, 32);
        builder.addRoute(VPN_ROUTE, 0);
        builder.setSession(getString(R.string.app_name));

        InetAddress[] dnsServers = config.getDnsServers();
        if (dnsServers.length == 0) {
            // no DNS server defined, use Google DNS
            builder.addDnsServer("8.8.8.8");
        } else {
            for (InetAddress dnsServer : dnsServers) {
                builder.addDnsServer(dnsServer);
            }
        }

        // non-blocking by default, but FileChannel is not selectable, that's stupid!
        // so switch to synchronous I/O to avoid polling
        builder.setBlocking(true);

        vpnInterface = builder.establish();

        setAsUndernlyingNetwork();
    }

    private void setAsUndernlyingNetwork() {
        if (Build.VERSION.SDK_INT >= 22) {
            Network vpnNetwork = findVpnNetwork();
            if (vpnNetwork != null) {
                // so that applications knows that network is available
                setUnderlyingNetworks(new Network[] {vpnNetwork});
            }
        } else {
            Log.w(TAG, "Cannot set underlying network, API version " + Build.VERSION.SDK_INT + " < 22");
        }
    }

    private Network findVpnNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network[] networks = cm.getAllNetworks();
        for (Network network : networks) {
            LinkProperties linkProperties = cm.getLinkProperties(network);
            List<LinkAddress> addresses = linkProperties.getLinkAddresses();
            for (LinkAddress addr : addresses) {
                if (addr.getAddress().equals(VPN_ADDRESS)) {
                    return network;
                }
            }
        }
        return null;
    }

    private void startForwarding() {
        forwarder = new Forwarder(this, vpnInterface.getFileDescriptor());
        forwarder.forward();
    }

    private void stopForwarding() {
        if (forwarder != null) {
            forwarder.stop();
        }
    }

    private void close() {
        if (!isRunning()) {
            // already closed
            return;
        }
        try {
            stopForwarding();
            vpnInterface.close();
            vpnInterface = null;
        } catch (IOException e) {
            Log.w(TAG, "Cannot close VPN file descriptor", e);
        }
    }
}
