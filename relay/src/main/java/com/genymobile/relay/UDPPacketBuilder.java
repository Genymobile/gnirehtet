package com.genymobile.relay;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public class UDPPacketBuilder {

    private final ByteBuffer buffer = ByteBuffer.allocate(IPv4Packet.MAX_PACKET_LENGTH);
    private final Packetizer packetizer;
    private boolean hasPending;
    private IPv4Packet cache;

    public UDPPacketBuilder(IPv4Header referenceIPv4Header, UDPHeader referenceUDPHeader) {
        packetizer = new Packetizer(referenceIPv4Header, referenceUDPHeader);
        packetizer.getResponseIPv4Header().switchSourceAndDestination();
        packetizer.getResponseTransportHeader().switchSourceAndDestination();
    }

    public boolean hasPending() {
        return hasPending;
    }

    public boolean readFrom(ReadableByteChannel channel) throws IOException {
        assert !hasPending;
        hasPending = true;
        if (channel.read(buffer) == -1) {
            return false;
        }
        buffer.flip();
        return true;
    }

    public IPv4Packet getPacket() {
        assert hasPending;
        if (cache == null) {
            cache = packetizer.packetize(buffer);
        }
        return cache;
    }

    public void clear() {
        hasPending = false;
        buffer.clear();
        cache = null;
    }
}
