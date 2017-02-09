package com.genymobile.gnirehtet;

import java.io.IOException;

public interface Tunnel {

    void open() throws IOException;

    void waitForOpened() throws InterruptedException;

    // blocking
    void send(byte[] packet, int len) throws IOException;

    // blocking
    int receive(byte[] packet) throws IOException;
}
