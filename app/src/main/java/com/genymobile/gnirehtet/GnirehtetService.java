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

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
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

    private static final String TAG = GnirehtetService.class.getSimpleName();

    private static final InetAddress VPN_ADDRESS = Net.toInetAddress(new byte[] {10, 0, 0, 2});
    // magic value: higher (like 0x8000 or 0xffff) or lower (like 1500) values show poorer performances
    private static final int MTU = 0x4000;

    private final Notifier notifier = new Notifier(this);
    private final Handler handler = new RelayTunnelConnectionStateHandler(this);

    private ParcelFileDescriptor vpnInterface = null;
    private Forwarder forwarder;
    private ConnectivityManager.NetworkCallback underlyingNetworkWatcher;

    public static void start(Context context, VpnConfiguration config) {
        Intent intent = new Intent(context, GnirehtetService.class);
        intent.setAction(ACTION_START_VPN);
        intent.putExtra(GnirehtetService.EXTRA_VPN_CONFIGURATION, config);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(createStopIntent(context));
        } else {
            context.startService(createStopIntent(context));
        }
    }

    static Intent createStopIntent(Context context) {
        Intent intent = new Intent(context, GnirehtetService.class);
        intent.setAction(ACTION_CLOSE_VPN);
        return intent;
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
            if (!isRunning() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // stop() reached us through startForegroundService(), which
                // obliges this service to call startForeground() within a few
                // seconds or be killed with a ForegroundServiceDidNotStartInTime
                // crash. With nothing running, close() would never post the
                // notification that does that, and the crash dialog then sits
                // on a headless device until someone taps it.
                notifier.acknowledgeForegroundStart();
            }
            close();
        }
        return START_NOT_STICKY;
    }

    private boolean isRunning() {
        return vpnInterface != null;
    }

    private void startVpn(VpnConfiguration config) {
        notifier.start();
        if (setupVpn(config)) {
            startForwarding();
        }
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private boolean setupVpn(VpnConfiguration config) {
        Builder builder = new Builder();
        builder.addAddress(VPN_ADDRESS, 32);
        builder.setSession(getString(R.string.app_name));

        CIDR[] routes = config.getRoutes();
        if (routes.length == 0) {
            // no routes defined, redirect the whole network traffic
            builder.addRoute("0.0.0.0", 0);
        } else {
            for (CIDR route : routes) {
                builder.addRoute(route.getAddress(), route.getPrefixLength());
            }
        }

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
        builder.setMtu(MTU);

        // Registered before establish() so the VPN network cannot appear
        // unobserved; the callback is also replayed for networks that already
        // exist, so the order is belt and braces rather than a requirement.
        String underlyingMode = config.getUnderlyingMode();
        if (VpnConfiguration.UNDERLYING_CALLBACK.equals(underlyingMode)) {
            watchForVpnNetwork();
        }

        vpnInterface = builder.establish();
        if (vpnInterface == null) {
            Log.w(TAG, "VPN starting failed, please retry");
            // establish() may return null if the application is not prepared or is revoked
            stopWatchingForVpnNetwork();
            return false;
        }

        if (VpnConfiguration.UNDERLYING_SCAN.equals(underlyingMode)) {
            declareUnderlyingNetworkByScan();
        } else if (VpnConfiguration.UNDERLYING_NONE.equals(underlyingMode)) {
            Log.i(TAG, "Not declaring an underlying network (mode none)");
        }
        return true;
    }

    /*
     * Why the VPN declares itself as its own underlying network
     *
     * Android answers the legacy connectivity API (getActiveNetworkInfo and
     * friends) for an app behind a VPN by looking at the VPN's declared
     * underlying networks: the first one declared, or the real default network
     * when nothing is declared. The relay transport here is an ADB socket, which
     * is not an Android network, so there is nothing truthful to declare; and a
     * tethered handset with no SIM and no Wi-Fi has no default network either.
     * With no declaration the legacy call returns null to every app, and apps
     * that still gate on it -- Maps, YouTube, Play -- report themselves offline
     * while the tunnel is carrying their traffic. The modern API meanwhile sees
     * a validated VPN. Declaring the VPN itself is a compatibility workaround
     * that hands those apps a connected TYPE_VPN NetworkInfo instead.
     *
     * v2.5.1 already did this, but on current releases it never took: it scanned
     * getAllNetworks() once, synchronously after establish(), and the network
     * agent is registered on ConnectivityService's own thread, so the scan ran
     * before the VPN existed, found nothing, and declared nothing. On an Android
     * 16 handset the agent appeared 14 ms after establish() returned, with
     * UnderlyingNetworks: Null.
     *
     * The callback mode waits to be told. The scan mode keeps the original
     * behaviour, and none declares nothing, so the three can be compared on one
     * device with the same relay.
     */

    private void watchForVpnNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        // A request with TRANSPORT_VPN and without NOT_VPN matches our own
        // tunnel, which is the only network with VPN_ADDRESS on it.
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build();
        underlyingNetworkWatcher = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
                if (!isRunning() || !hasVpnAddress(linkProperties)) {
                    return;
                }
                declareUnderlyingNetwork(network, "callback");
            }
        };
        try {
            cm.registerNetworkCallback(request, underlyingNetworkWatcher);
        } catch (RuntimeException e) {
            // Too many callbacks, or a SecurityException on an odd build. The
            // tunnel still works without the declaration; only legacy-API apps
            // are affected, which is the v2.5.1 situation.
            Log.w(TAG, "Cannot watch for the VPN network; no underlying network will be declared", e);
            underlyingNetworkWatcher = null;
        }
    }

    private void stopWatchingForVpnNetwork() {
        if (underlyingNetworkWatcher == null) {
            return;
        }
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            cm.unregisterNetworkCallback(underlyingNetworkWatcher);
        } catch (IllegalArgumentException e) {
            // already unregistered
        }
        underlyingNetworkWatcher = null;
    }

    private void declareUnderlyingNetworkByScan() {
        Network vpnNetwork = findVpnNetwork();
        if (vpnNetwork == null) {
            Log.w(TAG, "Scan found no network with " + VPN_ADDRESS + " yet; no underlying network declared");
            return;
        }
        declareUnderlyingNetwork(vpnNetwork, "scan");
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private void declareUnderlyingNetwork(Network vpnNetwork, String how) {
        if (Build.VERSION.SDK_INT < 22) {
            Log.w(TAG, "Cannot set underlying network, API version " + Build.VERSION.SDK_INT + " < 22");
            return;
        }
        // Logged with the platform's verdict: a rejected declaration is the
        // other way this can silently not take.
        boolean accepted = setUnderlyingNetworks(new Network[] {vpnNetwork});
        Log.i(TAG, "Declared " + vpnNetwork + " as underlying network (" + how + "): "
                + (accepted ? "accepted" : "rejected"));
    }

    private Network findVpnNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network[] networks = cm.getAllNetworks();
        for (Network network : networks) {
            if (hasVpnAddress(cm.getLinkProperties(network))) {
                return network;
            }
        }
        return null;
    }

    private static boolean hasVpnAddress(LinkProperties linkProperties) {
        if (linkProperties == null) {
            // a network torn down between being listed and being asked about
            return false;
        }
        List<LinkAddress> addresses = linkProperties.getLinkAddresses();
        for (LinkAddress addr : addresses) {
            if (addr.getAddress().equals(VPN_ADDRESS)) {
                return true;
            }
        }
        return false;
    }

    private void startForwarding() {
        forwarder = new Forwarder(this, vpnInterface.getFileDescriptor(), new RelayTunnelListener(handler));
        forwarder.forward();
    }

    private void close() {
        if (!isRunning()) {
            // already closed
            return;
        }

        notifier.stop();
        stopWatchingForVpnNetwork();

        try {
            forwarder.stop();
            forwarder = null;
            vpnInterface.close();
            vpnInterface = null;
        } catch (IOException e) {
            Log.w(TAG, "Cannot close VPN file descriptor", e);
        }
    }


    private static final class RelayTunnelConnectionStateHandler extends Handler {

        private final GnirehtetService vpnService;

        private RelayTunnelConnectionStateHandler(GnirehtetService vpnService) {
            this.vpnService = vpnService;
        }

        @Override
        public void handleMessage(Message message) {
            if (!vpnService.isRunning()) {
                // if the VPN is not running anymore, ignore obsolete events
                return;
            }
            switch (message.what) {
                case RelayTunnelListener.MSG_RELAY_TUNNEL_CONNECTED:
                    Log.d(TAG, "Relay tunnel connected");
                    vpnService.notifier.setFailure(false);
                    break;
                case RelayTunnelListener.MSG_RELAY_TUNNEL_DISCONNECTED:
                    Log.d(TAG, "Relay tunnel disconnected");
                    vpnService.notifier.setFailure(true);
                    break;
                default:
            }
        }
    }
}
