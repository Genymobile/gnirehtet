/*
 * Copyright (C) 2017 Genymobile
 * Licensed under the Apache License, Version 2.0.
 */
package com.genymobile.gnirehtet;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;

public class GnirehtetService extends VpnService {

    public static final boolean VERBOSE = false;

    private static final String ACTION_START_VPN = "com.genymobile.gnirehtet.START_VPN";
    private static final String EXTRA_VPN_CONFIGURATION = "vpnConfiguration";
    private static final String TAG = GnirehtetService.class.getSimpleName();

    private static final InetAddress VPN_ADDRESS =
            Net.toInetAddress(new byte[] {10, 0, 0, 2});
    private static final int MTU = 0x4000;
    private static final int MSG_RELAY_TUNNEL_DISCONNECT_TIMEOUT = 2;
    private static final long RELAY_DISCONNECT_SHUTDOWN_DELAY_MS = 5000;

    private final Notifier notifier = new Notifier(this);
    private final Handler handler = new RelayTunnelConnectionStateHandler(this);

    private ParcelFileDescriptor vpnInterface;
    private Forwarder forwarder;
    private boolean closing;

    public static void start(Context context, VpnConfiguration config) {
        Intent intent = new Intent(context, GnirehtetService.class)
                .setAction(ACTION_START_VPN)
                .putExtra(EXTRA_VPN_CONFIGURATION, config);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, GnirehtetService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_START_VPN.equals(intent.getAction())) {
            Log.w(TAG, "Ignoring empty or unknown start request");
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        if (isRunning()) {
            Log.d(TAG, "VPN already running, ignoring START request");
            return START_NOT_STICKY;
        }

        notifier.start();
        VpnConfiguration config = intent.getParcelableExtra(EXTRA_VPN_CONFIGURATION);
        if (config == null) {
            config = new VpnConfiguration();
        }

        if (setupVpn(config)) {
            startForwarding();
        } else {
            close();
        }
        return START_NOT_STICKY;
    }

    private synchronized boolean isRunning() {
        return vpnInterface != null && !closing;
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private boolean setupVpn(VpnConfiguration config) {
        Builder builder = new Builder()
                .addAddress(VPN_ADDRESS, 32)
                .setSession(getString(R.string.app_name))
                .setBlocking(true)
                .setMtu(MTU);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false);
        }

        CIDR[] routes = config.getRoutes();
        if (routes.length == 0) {
            builder.addRoute("0.0.0.0", 0);
        } else {
            for (CIDR route : routes) {
                builder.addRoute(route.getAddress(), route.getPrefixLength());
            }
        }

        InetAddress[] dnsServers = config.getDnsServers();
        if (dnsServers.length == 0) {
            builder.addDnsServer("8.8.8.8");
        } else {
            for (InetAddress dnsServer : dnsServers) {
                builder.addDnsServer(dnsServer);
            }
        }

        try {
            vpnInterface = builder.establish();
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            Log.e(TAG, "Could not establish VPN interface", e);
            return false;
        }

        if (vpnInterface == null) {
            Log.w(TAG, "VPN starting failed or authorization was revoked");
            return false;
        }

        // Do not point setUnderlyingNetworks() at the VPN itself. The tunnel is
        // carried over an ADB local socket, not an Android upstream Network.
        return true;
    }

    private void startForwarding() {
        ParcelFileDescriptor descriptor = vpnInterface;
        if (descriptor == null) {
            close();
            return;
        }
        forwarder = new Forwarder(
                this,
                descriptor.getFileDescriptor(),
                new RelayTunnelListener(handler));
        forwarder.forward();
    }

    @Override
    public void onRevoke() {
        Log.i(TAG, "VPN authorization revoked");
        close();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        close();
        super.onDestroy();
    }

    private synchronized void close() {
        if (closing) {
            return;
        }
        closing = true;

        Forwarder localForwarder = forwarder;
        forwarder = null;
        ParcelFileDescriptor localInterface = vpnInterface;
        vpnInterface = null;

        if (localForwarder != null) {
            localForwarder.stop();
        }
        if (localInterface != null) {
            try {
                localInterface.close();
            } catch (IOException e) {
                Log.w(TAG, "Cannot close VPN file descriptor", e);
            }
        }

        handler.removeMessages(MSG_RELAY_TUNNEL_DISCONNECT_TIMEOUT);
        notifier.stop();
        stopSelf();
        closing = false;
    }

    private static final class RelayTunnelConnectionStateHandler extends Handler {
        private final GnirehtetService vpnService;

        private RelayTunnelConnectionStateHandler(GnirehtetService vpnService) {
            this.vpnService = vpnService;
        }

        @Override
        public void handleMessage(Message message) {
            if (!vpnService.isRunning()) {
                return;
            }
            switch (message.what) {
                case RelayTunnelListener.MSG_RELAY_TUNNEL_CONNECTED:
                    removeMessages(MSG_RELAY_TUNNEL_DISCONNECT_TIMEOUT);
                    vpnService.notifier.setFailure(false);
                    break;
                case RelayTunnelListener.MSG_RELAY_TUNNEL_DISCONNECTED:
                    vpnService.notifier.setFailure(true);
                    removeMessages(MSG_RELAY_TUNNEL_DISCONNECT_TIMEOUT);
                    sendEmptyMessageDelayed(
                            MSG_RELAY_TUNNEL_DISCONNECT_TIMEOUT,
                            RELAY_DISCONNECT_SHUTDOWN_DELAY_MS);
                    break;
                case MSG_RELAY_TUNNEL_DISCONNECT_TIMEOUT:
                    Log.i(TAG, "Relay disconnected, stopping VPN");
                    vpnService.close();
                    break;
                default:
                    break;
            }
        }
    }
}
