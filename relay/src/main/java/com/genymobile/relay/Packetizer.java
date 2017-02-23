package com.genymobile.relay;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

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

    public IPv4Packet packetize(ByteBuffer payload, int maxChunkSize) {
        payloadBuffer.limit(maxChunkSize).position(0);
        int payloadLength = payload.remaining();
        int chunkSize = Math.min(payloadLength, maxChunkSize);
        int savedLimit = payload.limit();
        payload.limit(payload.position() + chunkSize);
        payloadBuffer.put(payload);
        payload.limit(savedLimit);
        return inflate(chunkSize);
    }

    public IPv4Packet packetize(ByteBuffer payload) {
        return packetize(payload, payloadBuffer.capacity());
    }

    public IPv4Packet packetize(ReadableByteChannel channel, int maxChunkSize) throws IOException {
        payloadBuffer.limit(maxChunkSize).position(0);
        int payloadLength = channel.read(payloadBuffer);
        if (payloadLength == -1) {
            return null;
        }
        payloadBuffer.flip();
        return inflate(payloadLength);
    }

    public IPv4Packet packetize(ReadableByteChannel channel) throws IOException {
        return packetize(channel, payloadBuffer.capacity());
    }

    private IPv4Packet inflate(int payloadLength) {
        buffer.limit(payloadBuffer.arrayOffset() + payloadBuffer.limit()).position(0);

        int ipv4HeaderLength = responseIPv4Header.getHeaderLength();
        int transportHeaderLength = responseTransportHeader.getHeaderLength();
        int totalLength = ipv4HeaderLength + transportHeaderLength + payloadLength;

        responseIPv4Header.setTotalLength(totalLength);
        responseTransportHeader.setPayloadLength(payloadLength);

        // TODO documentation: use the same buffer to avoid copies, don't use this IPv4Packet after another call to createPacket()
        IPv4Packet packet = new IPv4Packet(buffer);
        packet.recompute();
        return packet;
    }
}
