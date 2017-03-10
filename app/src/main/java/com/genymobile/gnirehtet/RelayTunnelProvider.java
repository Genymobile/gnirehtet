package com.genymobile.gnirehtet;

import android.net.VpnService;

import java.io.IOException;

/**
 * Provide a valid {@link RelayTunnel}, creating a new one if necessary.
 */
public class RelayTunnelProvider {

    private final VpnService vpnService;
    private RelayTunnel tunnel;
    private boolean first;

    public RelayTunnelProvider(VpnService vpnService) {
        this.vpnService = vpnService;
    }

    public synchronized RelayTunnel getCurrentTunnel() throws IOException, InterruptedException {
        if (!first) {
            // add delay between attempts
            Thread.sleep(5000);
            first = true;
        }
        if (tunnel == null) {
            tunnel = RelayTunnel.open(vpnService);
        }
        return tunnel;
    }

    public synchronized void invalidateTunnel() {
        tunnel.close();
        tunnel = null;
    }
}
