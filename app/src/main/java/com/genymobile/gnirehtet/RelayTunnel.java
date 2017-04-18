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

import android.net.VpnService;
import android.util.Log;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public final class RelayTunnel implements Tunnel {

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
        fakeRead(channel.socket());
        return new RelayTunnel(channel);
    }

    /**
     * The relay server is accessible through an "adb reverse" port redirection.
     * <p>
     * If the port redirection is enabled but the relay server is not started, then the call to
     * channel.connect() will succeed, but the first read() will return -1.
     * <p>
     * As a consequence, the connection state of the relay server would be invalid temporarily (we
     * would switch to CONNECTED state then switch back to DISCONNECTED).
     * <p>
     * To avoid this problem, we must actually try to read from the server, so that an error occurs
     * immediately if the relay server is not accessible.
     * <p>
     * To do so, we set the lowest timeout possible (1) that is not "infinity" (0), try to read,
     * then reset the timeout option. Since the client is always the first peer to send data, there
     * is never anything to read.
     *
     * @param socket the socket to read
     * @throws IOException if an I/O error occurs
     */
    private static void fakeRead(Socket socket) throws IOException {
        int timeout = socket.getSoTimeout();
        socket.setSoTimeout(1);
        boolean eof = false;
        try {
            eof = socket.getInputStream().read() == -1;
        } catch (SocketTimeoutException e) {
            // ignore, expected timeout
        } finally {
            socket.setSoTimeout(timeout);
        }
        if (eof) {
            throw new IOException("Cannot connect to the relay server");
        }
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
