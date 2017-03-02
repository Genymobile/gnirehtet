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

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

public class GnirehtetService extends VpnService {

    public static final boolean VERBOSE = false;

    public static final String ACTION_CLOSE_VPN = "com.genymobile.gnirehtet.CLOSE_VPN";

    private static final String TAG = VpnService.class.getName();

    private static final InetAddress VPN_ADDRESS = getInetAddress(new byte[] {10, 0, 0, 2});
    private static final InetAddress VPN_ROUTE = getInetAddress(new byte[] {0, 0, 0, 0}); // intercept everything

    private static final int MAX_PACKET_SIZE = 4096;

    private ParcelFileDescriptor vpnInterface = null;
    private Thread deviceToTunnelThread;
    private Thread tunnelToDeviceThread;

    @Override
    public void onCreate() {
        super.onCreate();
        setupVpn();
        startForwarding();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (ACTION_CLOSE_VPN.equals(action)) {
            close();
        }
        return START_NOT_STICKY;
    }

    private static InetAddress getInetAddress(byte[] raw) {
        try {
            return InetAddress.getByAddress(raw);
        } catch (UnknownHostException e) {
            throw new AssertionError("Invalid address");
        }
    }

    private void setupVpn() {
        Builder builder = new Builder();
        builder.addAddress(VPN_ADDRESS, 32);
        builder.addRoute(VPN_ROUTE, 0);
        builder.setSession(getString(R.string.app_name));
//        builder.addDnsServer("194.79.128.150");
        //builder.addDnsServer("192.168.0.127");
        builder.addDnsServer("8.8.8.8");

        // non-blocking by default, but FileChannel is not selectable, that's stupid!
        // so switch to synchronous I/O to avoid polling
        builder.setBlocking(true);

        vpnInterface = builder.establish();

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
            Log.d(TAG, "" + linkProperties);
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
        Tunnel tunnel = new RelayTunnel(new RelayClient(this));

        FileDescriptor fileDescriptor = vpnInterface.getFileDescriptor();
        deviceToTunnelThread = new Thread(new DeviceToTunnelForwarder(fileDescriptor, tunnel));
        deviceToTunnelThread.start();
        tunnelToDeviceThread = new Thread(new TunnelToDeviceForwarder(fileDescriptor, tunnel));
        tunnelToDeviceThread.start();
    }

    private void stopForwarding() {
        if (deviceToTunnelThread != null) {
            deviceToTunnelThread.interrupt();
            tunnelToDeviceThread.interrupt();
            deviceToTunnelThread = null;
            tunnelToDeviceThread = null;
        }
    }

    private void close() {
        if (vpnInterface == null) {
            // already closed
            return;
        }
        try {
            vpnInterface.close();
            vpnInterface = null;
        } catch (IOException e) {
            Log.w(TAG, "Cannot close VPN file descriptor", e);
        }
    }

    private static class DeviceToTunnelForwarder implements Runnable {

        private FileDescriptor vpnFileDescriptor;
        private Tunnel tunnel;

        DeviceToTunnelForwarder(FileDescriptor vpnFileDescriptor, Tunnel tunnel) {
            this.vpnFileDescriptor = vpnFileDescriptor;
            this.tunnel = tunnel;
        }

        @Override
        public void run() {
            try {
                tunnel.open();
                Log.d(TAG, "Device to tunnel forwarding started");

                FileInputStream vpnInput = new FileInputStream(vpnFileDescriptor);

                byte[] buffer = new byte[MAX_PACKET_SIZE];
                while (!Thread.interrupted()) {
                    // blocking read
                    int r = vpnInput.read(buffer);
                    if (r == -1) {
                        Log.d(TAG, "Tunnel closed");
                        break;
                    }
                    if (r > 0) {
                        tunnel.send(buffer, r);
                    }
                }
                Log.d(TAG, "Device to tunnel forwarding stopped");
            } catch (IOException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    }

    private static class TunnelToDeviceForwarder implements Runnable {

        private FileDescriptor vpnFileDescriptor;
        private Tunnel tunnel;

        TunnelToDeviceForwarder(FileDescriptor vpnFileDescriptor, Tunnel tunnel) {
            this.vpnFileDescriptor = vpnFileDescriptor;
            this.tunnel = tunnel;
        }

        @Override
        public void run() {
            try {
                tunnel.waitForOpened();
                Log.d(TAG, "Tunnel to device forwarding started");

                FileOutputStream vpnOutput = new FileOutputStream(vpnFileDescriptor);
                IPPacketOutputStream packetOutputStream = new IPPacketOutputStream(vpnOutput);

                byte[] buffer = new byte[MAX_PACKET_SIZE];
                while (!Thread.interrupted()) {
                    // blocking receive
                    int w = tunnel.receive(buffer);
                    if (w == -1) {
                        Log.d(TAG, "Tunnel closed");
                        break;
                    }
                    if (w > 0) {
                        if (GnirehtetService.VERBOSE) {
                            Log.d(TAG, "WRITING " + w + "..." + Binary.toString(buffer, w));
                        }
                        packetOutputStream.write(buffer, 0, w);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, e.getMessage(), e);
            } catch (InterruptedException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    }
}
