package com.genymobile.relay;

/**
 * Source that may produce packets.
 * <p>
 * When {@link TCPConnection} sends a packet to the {@link Client} while its buffers are full, then
 * it fails. To recover, once some space becomes available, the {@link Client} must pull the
 * available packets.
 * <p>
 * This interface provides the abstraction of a packet source from which it call pull packets, and
 * is implemented by {@link TCPConnection}.
 */
public interface PacketSource {

    IPv4Packet get();

    void next();
}
