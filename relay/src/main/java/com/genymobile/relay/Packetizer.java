package com.genymobile.relay;

import java.nio.ByteBuffer;

public class Packetizer {

    private final ByteBuffer buffer = ByteBuffer.allocate(IPv4Packet.MAX_PACKET_LENGTH);
    private final ByteBuffer payloadBuffer;

    private final IPv4Header responseIPv4Header;
    private final TransportHeader responseTransportHeader;

    public Packetizer(IPv4Header ipv4Header, TransportHeader transportHeader) {
        responseIPv4Header = ipv4Header.copyTo(buffer);
        responseTransportHeader = transportHeader.copyTo(buffer);
        payloadBuffer = buffer.slice();
    }

    public IPv4Header getResponseIPv4Header() {
        return responseIPv4Header;
    }

    public TransportHeader getResponseTransportHeader() {
        return responseTransportHeader;
    }

    public IPv4Packet packetize(ByteBuffer payload) {
        int ipv4HeaderLength = responseIPv4Header.getHeaderLength();
        int transportHeaderLength = responseTransportHeader.getHeaderLength();
        int payloadLength = payload.remaining();
        int totalLength = ipv4HeaderLength + transportHeaderLength + payloadLength;

        responseIPv4Header.setTotalLength(totalLength);
        responseTransportHeader.setPayloadLength(payload.remaining());

        payloadBuffer.clear();
        payloadBuffer.put(payload);
        buffer.limit(payloadBuffer.arrayOffset() + payloadBuffer.limit()).position(0);
        // TODO documentation: use the same buffer to avoid copies, don't use this IPv4Packet after another call to createPacket()
        IPv4Packet packet = new IPv4Packet(buffer);
        packet.recompute();
        return packet;
    }

    public IPv4Packet packetize(ByteBuffer payload, int maxChunkSize) {
        int nextPayloadLength = Math.min(maxChunkSize, payload.remaining());
        int savedLimit = payload.limit();
        payload.limit(payload.position() + nextPayloadLength);
        IPv4Packet packet = packetize(payload);
        payload.limit(savedLimit);
        return packet;
    }
}
