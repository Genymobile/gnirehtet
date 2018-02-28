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

import com.genymobile.gnirehtet.relay.Log;

import java.io.EOFException;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;

public class AdbMonitor {

    public interface AdbDevicesCallback {
        void onNewDeviceConnected(String serial);
    }

    private static final String TAG = AdbMonitor.class.getSimpleName();
    private static final int ADBD_PORT = 5037;

    private static final String TRACK_DEVICES_REQUEST = "0012host:track-devices";
    private static final int BUFFER_SIZE = 1024;
    private static final int LENGTH_FIELD_SIZE = 4;
    private static final int OKAY_SIZE = 4;
    private static final long RETRY_DELAY_ADB_DAEMON_OK = 1000;
    private static final long RETRY_DELAY_ADB_DAEMON_KO = 5000;

    private AdbDevicesCallback callback;

    private static final byte[] BUFFER = new byte[BUFFER_SIZE]; // used only locally to avoid allocations, so static is ok
    private final ByteBuffer socketBuffer = ByteBuffer.allocate(BUFFER_SIZE);

    public AdbMonitor(AdbDevicesCallback callback) {
        this.callback = callback;
    }

    public void monitor() {
        while (true) {
            try {
                trackDevices();
            } catch (Exception e) {
                Log.e(TAG, "Failed to monitor adb devices", e);
                repairAdbDaemon();
            }
        }
    }

    private void trackDevices() throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        try {
            socketChannel.connect(new InetSocketAddress(Inet4Address.getLoopbackAddress(), ADBD_PORT));
            trackDevicesOnChannel(socketChannel);
        } finally {
            socketChannel.close();
        }
    }

    private void trackDevicesOnChannel(ByteChannel channel) throws IOException {
        socketBuffer.clear();
        writeRequest(channel, TRACK_DEVICES_REQUEST);
        // the daemon initially sends "OKAY" if it understands the request
        if (!consumeOkay(channel)) {
            return;
        }
        while (true) {
            String packet = nextPacket(channel);
            handlePacket(packet);
        }
    }

    private static void writeRequest(WritableByteChannel channel, String request) throws IOException {
        ByteBuffer requestBuffer = ByteBuffer.wrap(request.getBytes(StandardCharsets.US_ASCII));
        channel.write(requestBuffer);
    }

    private boolean consumeOkay(ReadableByteChannel channel) throws IOException {
        while (channel.read(socketBuffer) != -1) {
            if (socketBuffer.position() < OKAY_SIZE) {
                // not enough data
                continue;
            }
            socketBuffer.flip();
            socketBuffer.get(BUFFER, 0, OKAY_SIZE);
            socketBuffer.compact();
            socketBuffer.flip();
            String text = new String(BUFFER, 0, OKAY_SIZE, StandardCharsets.US_ASCII);
            return "OKAY".equals(text);
        }
        return false;
    }

    private String nextPacket(ReadableByteChannel channel) throws IOException {
        String packet;
        while ((packet = readPacket(socketBuffer)) == null) {
            // need more data
            fillBufferFrom(channel);
        }
        return packet;
    }

    private void fillBufferFrom(ReadableByteChannel channel) throws IOException {
        socketBuffer.compact();
        int r;
        if (channel.read(socketBuffer) == -1) {
            throw new EOFException("ADB daemon closed the track-devices connexion");
        }
        socketBuffer.flip();
    }

    static String readPacket(ByteBuffer input) {
        if (input.remaining() < LENGTH_FIELD_SIZE) {
            return null;
        }
        // each packet contains 4 bytes representing the String length in hexa, followed by the device serial, `\t', the state, '\n'
        // for example: "00180123456789abcdef\tdevice\n": 0018 indicates that the data is 0x18 (24) bytes length
        input.get(BUFFER, 0, LENGTH_FIELD_SIZE);
        int length = parseLength(BUFFER);
        if (length > BUFFER.length) {
            throw new IllegalArgumentException("Packet size should not be that big: " + length);
        }
        if (input.remaining() < length) {
            // not enough data
            input.rewind();
            return null;
        }
        input.get(BUFFER, 0, length);
        return new String(BUFFER, 0, length, StandardCharsets.UTF_8);
    }

    void handlePacket(String packet) {
        String[] tokens = packet.split("\\s+");
        if (tokens.length == 2) {
            String state = tokens[1];
            if ("device".equals(state)) {
                String serial = tokens[0];
                callback.onNewDeviceConnected(serial);
            }
        }
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static int parseLength(byte[] data) {
        if (data.length < LENGTH_FIELD_SIZE) {
            throw new IllegalArgumentException("Length field must be at least 4 bytes length");
        }
        int result = 0;
        for (int i = 0; i < LENGTH_FIELD_SIZE; ++i) {
            char c = (char) data[i];
            result = (result << 4) + Character.digit(c, 0x10);
        }
        return result;
    }

    private static void repairAdbDaemon() {
        if (startAdbDaemon()) {
            sleep(RETRY_DELAY_ADB_DAEMON_OK);
        } else {
            sleep(RETRY_DELAY_ADB_DAEMON_KO);
        }
    }

    private static boolean startAdbDaemon() {
        Log.i(TAG, "Restarting adb deamon");
        try {
            Process process = new ProcessBuilder("adb", "start-server")
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT).start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                Log.e(TAG, "Could not restart adb daemon (exited on error)");
                return false;
            }
            return true;
        } catch (InterruptedException | IOException e) {
            Log.e(TAG, "Could not restart adb daemon", e);
            return false;
        }
    }

    private static void sleep(long delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            // should never happen
        }
    }
}
