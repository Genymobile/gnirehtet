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

    private final VpnService vpnService;
    private RelayTunnel tunnel;
    private boolean first = true;
    private long lastFailureTimestamp;
    private RelayTunnelListener listener;

    public RelayTunnelProvider(VpnService vpnService) {
        this.vpnService = vpnService;
    }

    public synchronized RelayTunnel getCurrentTunnel() throws IOException, InterruptedException {
        if (tunnel != null) {
            return tunnel;
        }

        waitUntilNextAttemptSlot();

        // the tunnel variable may have changed during the waiting
        if (tunnel == null) {
            openTunnel();
        }
        return tunnel;
    }

    private void openTunnel() throws IOException {
        // the first connection must either notify "connected" or "disconnected"
        boolean notifyDisconnectedOnError = first;
        first = false;
        openTunnel(notifyDisconnectedOnError);
    }

    private void openTunnel(boolean notifyDisconnectedOnError) throws IOException {
        try {
            tunnel = RelayTunnel.open(vpnService);
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

    private void touchFailure() {
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

    public void setListener(RelayTunnelListener listener) {
        this.listener = listener;
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
