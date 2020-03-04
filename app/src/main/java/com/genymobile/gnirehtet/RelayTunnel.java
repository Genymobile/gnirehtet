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

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.VpnService;
import android.util.Log;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class RelayTunnel implements Tunnel {

    private static final String TAG = RelayTunnel.class.getSimpleName();

    private static final String LOCAL_ABSTRACT_NAME = "gnirehtet";

    private final LocalSocket localSocket = new LocalSocket();

    private RelayTunnel() {
        // exposed through open() static method
    }

    @SuppressWarnings("unused")
    public static RelayTunnel open(VpnService vpnService) throws IOException {
        Log.d(TAG, "Opening a new relay tunnel...");
        // since we use a local socket, we don't need to protect the socket from the vpnService anymore
        // but this is an implementation detail, so keep the method signature
        return new RelayTunnel();
    }

    public void connect() throws IOException {
        localSocket.connect(new LocalSocketAddress(LOCAL_ABSTRACT_NAME));
        readClientId(localSocket.getInputStream());
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
     * To avoid this problem, we must actually read from the server, so that an error occurs
     * immediately if the relay server is not accessible.
     * <p>
     * Therefore, the relay server immediately sends the client id: consume it and log it.
     *
     * @param inputStream the input stream to receive data from the relay server
     * @throws IOException if an I/O error occurs
     */
    private static void readClientId(InputStream inputStream) throws IOException {
        Log.d(TAG, "Requesting client id");
        int clientId = new DataInputStream(inputStream).readInt();
        Log.d(TAG, "Connected to the relay server as #" + Binary.unsigned(clientId));
    }

    @Override
    public void send(byte[] packet, int len) throws IOException {
        if (GnirehtetService.VERBOSE) {

            Log.d(TAG, "Sending packet:" + Binary.buildPacketString(packet, len));
        }
        localSocket.getOutputStream().write(packet, 0, len);
    }

    @Override
    public int receive(byte[] packet) throws IOException {
        int r = localSocket.getInputStream().read(packet);
        if (GnirehtetService.VERBOSE) {
            Log.d(TAG, "Receiving packet:" + Binary.buildPacketString(packet, r));
        }
        return r;
    }

    @Override
    public void close() {
        try {
            if (localSocket.getFileDescriptor() != null) {
                // close the streams to interrupt pending read and writes
                localSocket.shutdownInput();
                localSocket.shutdownOutput();
            }
            localSocket.close();
        } catch (IOException e) {
            // what could we do?
            throw new RuntimeException(e);
        }
    }
}
