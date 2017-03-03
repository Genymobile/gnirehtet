package com.genymobile.gnirehtet;

import android.net.VpnService;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public class Forwarder {

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(3);

    private static final String TAG = Forwarder.class.getSimpleName();

    private static final int BUFSIZE = 4096;

    private VpnService vpnService;
    private FileDescriptor vpnFileDescriptor;

    private final AtomicBoolean stopped = new AtomicBoolean();

    private Future<?> deviceToTunnelFuture;
    private Future<?> tunnelToDeviceFuture;

    public Forwarder(VpnService vpnService, FileDescriptor vpnFileDescriptor) {
        this.vpnService = vpnService;
        this.vpnFileDescriptor = vpnFileDescriptor;
    }

    public void forward() {
        EXECUTOR_SERVICE.execute(new Runnable() {
            @Override
            public void run() {
                forwardSync();
            }
        });
    }

    private void forwardSync() {
        boolean first = true;
        while (!stopped.get()) {
            try {
                if (!first) {
                    Thread.sleep(5000);
                } else {
                    first = false;
                }
                if (!stopped.get()) {
                    connectAndRelay();
                }
            } catch (IOException | InterruptedException e) {
                Log.d(TAG, "Forwarding failed", e);
            }
        }
        Log.d(TAG, "Forwarding stopped");
    }

    public void stop() {
        stopped.set(true);
    }

    private void connectAndRelay() throws IOException, InterruptedException {
        Tunnel tunnel = RelayTunnel.open(vpnService);

        Semaphore semaphore = new Semaphore(0);

        startForwardingTasks(tunnel, semaphore);

        // wait for the completion of any of the two tasks
        semaphore.acquire();

        // cause all asynchronous tasks to complete
        deviceToTunnelFuture.cancel(true);
        tunnelToDeviceFuture.cancel(true);
        tunnel.close();
    }

    private synchronized void startForwardingTasks(final Tunnel tunnel, final Semaphore semaphore) {
        deviceToTunnelFuture = EXECUTOR_SERVICE.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    forwardDeviceToTunnel(tunnel);
                } catch (IOException e) {
                    Log.e(TAG, "Device to tunnel exception", e);
                } finally {
                    semaphore.release();
                }
            }
        });
        tunnelToDeviceFuture = EXECUTOR_SERVICE.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    forwardTunnelToDevice(tunnel);
                } catch (IOException e) {
                    Log.e(TAG, "Tunnel to device exception", e);
                } finally {
                    semaphore.release();
                }
            }
        });
    }

    private void forwardDeviceToTunnel(Tunnel tunnel) throws IOException {
        Log.d(TAG, "Device to tunnel forwarding started");
        FileInputStream vpnInput = new FileInputStream(vpnFileDescriptor);
        byte[] buffer = new byte[BUFSIZE];
        while (true) {
            // blocking read
            int r = vpnInput.read(buffer);
            if (r == -1) {
                Log.d(TAG, "Tunnel closed");
                break;
            }
            if (r > 0) {
                // blocking send
                tunnel.send(buffer, r);
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
            }
        }
        Log.d(TAG, "Tunnel to device forwarding stopped");
    }
}
