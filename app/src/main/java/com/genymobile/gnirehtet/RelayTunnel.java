package com.genymobile.gnirehtet;

import android.net.VpnService;
import android.util.Log;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class RelayTunnel implements Tunnel {

    private static final String TAG = RelayTunnel.class.getSimpleName();

    private static final int DEFAULT_PORT = 31416;

    private final SocketChannel channel;

    private RelayTunnel(SocketChannel channel) {
        this.channel = channel;
    }

    public static RelayTunnel open(VpnService vpnService) throws IOException {
        Log.d(TAG, "Opening a new relay tunnel...");
        SocketChannel channel = SocketChannel.open();
        vpnService.protect(channel.socket());
        channel.connect(new InetSocketAddress(Inet4Address.getLocalHost(), DEFAULT_PORT));
        return new RelayTunnel(channel);
    }

    @Override
    public void send(byte[] packet, int len) throws IOException {
        if (GnirehtetService.VERBOSE) {
            Log.d(TAG, "Sending..." + Binary.toString(packet, len));
        }
        ByteBuffer buffer = ByteBuffer.wrap(packet, 0, len);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    @Override
    public int receive(byte[] packet) throws IOException {
        int r = channel.read(ByteBuffer.wrap(packet));
        if (GnirehtetService.VERBOSE) {
            Log.d(TAG, "Receiving..." + Binary.toString(packet, r));
        }
        return r;
    }

    @Override
    public void close() {
        try {
            channel.close();
        } catch (IOException e) {
            // what could we do?
            throw new RuntimeException(e);
        }
    }
}
