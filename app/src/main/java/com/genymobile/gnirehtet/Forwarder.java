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

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Forwarder {

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(3);

    private static final String TAG = Forwarder.class.getSimpleName();

    private static final int BUFSIZE = 4096;

    private static final byte[] DUMMY_ADDRESS = {42, 42, 42, 42};
    private static final int DUMMY_PORT = 4242;

    private final FileDescriptor vpnFileDescriptor;
    private final Tunnel tunnel;

    private Future<?> deviceToTunnelFuture;
    private Future<?> tunnelToDeviceFuture;

    public Forwarder(VpnService vpnService, FileDescriptor vpnFileDescriptor) {
        this.vpnFileDescriptor = vpnFileDescriptor;
        tunnel = new PersistentRelayTunnel(vpnService);
    }

    public void forward() {
        deviceToTunnelFuture = EXECUTOR_SERVICE.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    forwardDeviceToTunnel(tunnel);
                } catch (InterruptedIOException e) {
                    Log.d(TAG, "Device to tunnel interrupted");
                } catch (IOException e) {
                    Log.e(TAG, "Device to tunnel exception", e);
                }
            }
        });
        tunnelToDeviceFuture = EXECUTOR_SERVICE.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    forwardTunnelToDevice(tunnel);
                } catch (InterruptedIOException e) {
                    Log.d(TAG, "Device to tunnel interrupted");
                } catch (IOException e) {
                    Log.e(TAG, "Tunnel to device exception", e);
                }
            }
        });
    }

    public void stop() {
        tunnel.close();
        tunnelToDeviceFuture.cancel(true);
        deviceToTunnelFuture.cancel(true);
        wakeUpReadWorkaround();
    }

    private void forwardDeviceToTunnel(Tunnel tunnel) throws IOException {
        Log.d(TAG, "Device to tunnel forwarding started");
        FileInputStream vpnInput = new FileInputStream(vpnFileDescriptor);
        byte[] buffer = new byte[BUFSIZE];
        while (true) {
            // blocking read
            int r = vpnInput.read(buffer);
            if (r == -1) {
                Log.d(TAG, "VPN closed");
                break;
            }
            if (r > 0) {
                // blocking send
                tunnel.send(buffer, r);
            } else {
                Log.w(TAG, "Empty read");
            }
        }
        Log.d(TAG, "Device to tunnel forwarding stopped");
    }

    private void forwardTunnelToDevice(Tunnel tunnel) throws IOException {
        Log.d(TAG, "Tunnel to device forwarding started");
        FileOutputStream vpnOutput = new FileOutputStream(vpnFileDescriptor);
        IPPacketOutputStream packetOutputStream = new IPPacketOutputStream(vpnOutput);

        byte[] buffer = new byte[BUFSIZE];
        while (true) {
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
                // blocking write
                packetOutputStream.write(buffer, 0, w);
            } else {
                Log.w(TAG, "Empty write");
            }
        }
        Log.d(TAG, "Tunnel to device forwarding stopped");
    }

    /**
     * Neither vpnInterface.close() nor vpnInputStream.close() wake up a blocking
     * vpnInputStream.read().
     * <p>
     * Therefore, we need to make Android send a packet to the VPN interface (here by sending a UDP
     * packet), so that any blocking read will be woken up.
     * <p>
     * Since the tunnel is closed at this point, it will never reach the network.
     */
    private void wakeUpReadWorkaround() {
        // network actions may not be called from the main thread
        EXECUTOR_SERVICE.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    DatagramSocket socket = new DatagramSocket();
                    InetAddress dummyAddr = InetAddress.getByAddress(DUMMY_ADDRESS);
                    DatagramPacket packet = new DatagramPacket(new byte[0], 0, dummyAddr, DUMMY_PORT);
                    socket.send(packet);
                } catch (IOException e) {
                    // ignore
                }
            }
        });
    }
}
