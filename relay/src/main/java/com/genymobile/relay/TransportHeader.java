package com.genymobile.relay;

import java.nio.ByteBuffer;

public interface TransportHeader {

    int getSourcePort();

    int getDestinationPort();

    void setSourcePort(int port);

    void setDestinationPort(int port);

    int getHeaderLength();

    void setPayloadLength(int payloadLength);

    void writeTo(ByteBuffer buffer);

    TransportHeader copy();

    void computeChecksum(IPv4Header ipv4Header, ByteBuffer payload);

    default void switchSourceAndDestination() {
        int tmp = getSourcePort();
        setSourcePort(getDestinationPort());
        setDestinationPort(tmp);
    }
}
