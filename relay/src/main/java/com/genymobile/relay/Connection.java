package com.genymobile.relay;

public interface Connection {

    void sendToNetwork(IPv4Packet packet);
    void disconnect();
    boolean isExpired();
}
