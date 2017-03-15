package com.genymobile.gnirehtet;

import java.io.IOException;

public interface Tunnel {

    // blocking
    void send(byte[] packet, int len) throws IOException;

    // blocking
    int receive(byte[] packet) throws IOException;

    // blocking
    void close();
}
