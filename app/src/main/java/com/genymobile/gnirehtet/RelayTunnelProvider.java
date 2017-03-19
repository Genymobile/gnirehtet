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

    private final VpnService vpnService;
    private RelayTunnel tunnel;
    private boolean first = true;

    public RelayTunnelProvider(VpnService vpnService) {
        this.vpnService = vpnService;
    }

    public synchronized RelayTunnel getCurrentTunnel() throws IOException, InterruptedException {
        if (tunnel == null) {
            if (!first) {
                // add delay between attempts
                Thread.sleep(5000);
            } else {
                first = false;
            }
            tunnel = RelayTunnel.open(vpnService);
        }
        return tunnel;
    }

    public synchronized void invalidateTunnel() {
        if (tunnel != null) {
            tunnel.close();
            tunnel = null;
        }
    }
}
