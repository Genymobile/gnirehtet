package com.genymobile.relay;

import java.nio.ByteBuffer;

public class UDPHeader implements TransportHeader {

    private static final int UDP_HEADER_LENGTH = 8;

    private ByteBuffer raw;
    private int sourcePort;
    private int destinationPort;

    public UDPHeader(ByteBuffer raw) {
        this.raw = raw;
        raw.limit(UDP_HEADER_LENGTH);
        sourcePort = Short.toUnsignedInt(raw.getShort(0));
        destinationPort = Short.toUnsignedInt(raw.getShort(2));
    }

    @Override
    public int getSourcePort() {
        return sourcePort;
    }

    @Override
    public int getDestinationPort() {
        return destinationPort;
    }

    @Override
    public void setSourcePort(int sourcePort) {
        this.sourcePort = sourcePort;
        raw.putShort(0, (short) sourcePort);
    }

    @Override
    public void setDestinationPort(int destinationPort) {
        this.destinationPort = destinationPort;
        raw.putShort(2, (short) destinationPort);
    }

    @Override
    public int getHeaderLength() {
        return UDP_HEADER_LENGTH;
    }

    @Override
    public void setPayloadLength(int payloadLength) {
        int length = getHeaderLength() + payloadLength;
        raw.putShort(4, (short) length);
    }

    @Override
    public void writeTo(ByteBuffer buffer) {
        raw.position(0).limit(getHeaderLength());
        buffer.put(raw);
    }

    @Override
    public UDPHeader copy() {
        return new UDPHeader(Binary.copy(raw));
    }

    @Override
    public void computeChecksum(IPv4Header ipv4Header, ByteBuffer payload) {
        // disable checksum validation
        raw.putShort(6, (short) 0);
    }
}
