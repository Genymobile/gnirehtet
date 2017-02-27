package com.genymobile.relay;

public interface PacketSource {

    IPv4Packet get();
    void next();
}
