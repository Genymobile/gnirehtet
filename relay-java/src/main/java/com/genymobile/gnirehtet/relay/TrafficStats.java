/*
 * Based on Gnirehtet.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.genymobile.gnirehtet.relay;

import java.util.concurrent.atomic.AtomicLong;

public class TrafficStats {

    public static final class Snapshot {
        private final long networkToClientBytes;
        private final long clientToNetworkBytes;
        private final long networkToClientPackets;
        private final long clientToNetworkPackets;

        private Snapshot(long networkToClientBytes, long clientToNetworkBytes, long networkToClientPackets,
                long clientToNetworkPackets) {
            this.networkToClientBytes = networkToClientBytes;
            this.clientToNetworkBytes = clientToNetworkBytes;
            this.networkToClientPackets = networkToClientPackets;
            this.clientToNetworkPackets = clientToNetworkPackets;
        }

        public long getNetworkToClientBytes() {
            return networkToClientBytes;
        }

        public long getClientToNetworkBytes() {
            return clientToNetworkBytes;
        }

        public long getNetworkToClientPackets() {
            return networkToClientPackets;
        }

        public long getClientToNetworkPackets() {
            return clientToNetworkPackets;
        }
    }

    private final AtomicLong networkToClientBytes = new AtomicLong();
    private final AtomicLong clientToNetworkBytes = new AtomicLong();
    private final AtomicLong networkToClientPackets = new AtomicLong();
    private final AtomicLong clientToNetworkPackets = new AtomicLong();

    public void recordNetworkToClient(int bytes) {
        networkToClientBytes.addAndGet(bytes);
        networkToClientPackets.incrementAndGet();
    }

    public void recordClientToNetwork(int bytes) {
        clientToNetworkBytes.addAndGet(bytes);
        clientToNetworkPackets.incrementAndGet();
    }

    public Snapshot snapshot() {
        return new Snapshot(networkToClientBytes.get(), clientToNetworkBytes.get(), networkToClientPackets.get(),
                clientToNetworkPackets.get());
    }

    public void reset() {
        networkToClientBytes.set(0);
        clientToNetworkBytes.set(0);
        networkToClientPackets.set(0);
        clientToNetworkPackets.set(0);
    }
}
