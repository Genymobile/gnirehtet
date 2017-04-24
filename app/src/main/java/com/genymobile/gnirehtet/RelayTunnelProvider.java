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

import java.io.IOException;

/**
 * Provide a valid {@link RelayTunnel}, creating a new one if necessary.
 */
public class RelayTunnelProvider {

    private static final int DELAY_BETWEEN_ATTEMPTS_MS = 5000;

    private final Object getCurrentTunnelLock = new Object(); // protects getCurrentTunnel()

    private final VpnService vpnService;
    private final RelayTunnelListener listener;
    private RelayTunnel tunnel; // protected both by "this" and "getCurrentTunnelLock"
    private boolean first = true; // protected by "getCurrentTunnelLock"
    private long lastFailureTimestamp; // protected by "this"

    public RelayTunnelProvider(VpnService vpnService, RelayTunnelListener listener) {
        this.vpnService = vpnService;
        this.listener = listener;
    }

    public RelayTunnel getCurrentTunnel() throws IOException, InterruptedException {
        /*
         * To make sure that both the sending and receiving threads use the same tunnel, we must
         * guarantee that this method may not be called several times concurrently.
         *
         * However, since it executes potentially long-running blocking calls, we still want to be
         * able to call invalidateTunnel() concurrently, which requires to protect some fields.
         *
         * Therefore, use one mutex ("getCurrentTunnelLock") to avoid concurrent calls to
         * getCurrentTunnel(), and another one ("this") to protect fields shared with
         * invalidateTunnel().
         */
        synchronized (getCurrentTunnelLock) {
            synchronized (this) {
                if (tunnel != null) {
                    return tunnel;
                }

                waitUntilNextAttemptSlot();

                // "tunnel" has not changed during waiting (only getCurrentTunnel() may write it)
                tunnel = RelayTunnel.open(vpnService);
            }

            // the first connection must either notify "connected" or "disconnected"
            boolean notifyDisconnectedOnError = first;
            first = false;
            connectTunnel(notifyDisconnectedOnError);
        }
        return tunnel;
    }

    private void connectTunnel(boolean notifyDisconnectedOnError) throws IOException {
        try {
            tunnel.connect();
            notifyConnected();
        } catch (IOException e) {
            touchFailure();
            if (notifyDisconnectedOnError) {
                notifyDisconnected();
            }
            throw e;
        }
    }

    public synchronized void invalidateTunnel() {
        if (tunnel != null) {
            touchFailure();
            tunnel.close();
            tunnel = null;
            notifyDisconnected();
        }
    }

    /**
     * Call {@link #invalidateTunnel()} only if {@code tunnelToInvalidate} is the current tunnel (or
     * is {@code null}).
     *
     * @param tunnelToInvalidate the tunnel to invalidate
     */
    public synchronized void invalidateTunnel(Tunnel tunnelToInvalidate) {
        if (tunnel == tunnelToInvalidate || tunnelToInvalidate == null) {
            invalidateTunnel();
        }
    }

    private synchronized void touchFailure() {
        lastFailureTimestamp = System.currentTimeMillis();
    }

    private void waitUntilNextAttemptSlot() throws InterruptedException {
        if (first) {
            // do not wait on first attempt
            return;
        }
        long delay = lastFailureTimestamp + DELAY_BETWEEN_ATTEMPTS_MS - System.currentTimeMillis();
        while (delay > 0) {
            wait(delay);
            delay = lastFailureTimestamp + DELAY_BETWEEN_ATTEMPTS_MS - System.currentTimeMillis();
        }
    }

    private void notifyConnected() {
        if (listener != null) {
            listener.notifyRelayTunnelConnected();
        }
    }

    private void notifyDisconnected() {
        if (listener != null) {
            listener.notifyRelayTunnelDisconnected();
        }
    }
}
