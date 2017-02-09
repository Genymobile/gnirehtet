package com.genymobile.gnirehtet;

import android.net.VpnService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public class RelayClient {

    private static final String TAG = RelayClient.class.getName();

    private final VpnService vpnService;
    private SocketChannel channel;

    public RelayClient(VpnService vpnService) {
        this.vpnService = vpnService;
    }

    public synchronized void connect() throws IOException {
        channel = SocketChannel.open();
        vpnService.protect(channel.socket());
        //channel.configureBlocking(false);
        channel.connect(new InetSocketAddress("localhost", 1080));
        notifyAll();
    }

    public synchronized SocketChannel getChannel() {
        return channel;
    }

    public synchronized void waitForConnected() throws InterruptedException {
        while (channel == null) {
            wait();
        }
    }
}
