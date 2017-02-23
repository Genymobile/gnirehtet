package com.genymobile.relay;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

public class DatagramBufferTest {

    private static ByteBuffer createDatagram(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; ++i) {
            data[i] = (byte) i;
        }
        return ByteBuffer.wrap(data);
    }

    @Test
    public void testSimple() throws IOException {
        ByteBuffer datagram = createDatagram(5);

        DatagramBuffer datagramBuffer = new DatagramBuffer(9);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        WritableByteChannel channel = Channels.newChannel(bos);

        datagramBuffer.readFrom(datagram);
        datagramBuffer.writeTo(channel);

        byte[] result = bos.toByteArray();
        Assert.assertArrayEquals(datagram.array(), result);
    }

    @Test
    public void testDatagramBoundaries() throws IOException {
        DatagramBuffer datagramBuffer = new DatagramBuffer(32);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        WritableByteChannel channel = Channels.newChannel(bos);

        ByteBuffer datagram5 = createDatagram(5);
        ByteBuffer datagram0 = createDatagram(0);
        ByteBuffer datagram3 = createDatagram(3);
        ByteBuffer datagram4 = createDatagram(4);

        datagramBuffer.readFrom(datagram5);
        datagramBuffer.readFrom(datagram0);
        datagramBuffer.readFrom(datagram3);
        datagramBuffer.readFrom(datagram4);

        datagramBuffer.writeTo(channel);
        byte[] result = bos.toByteArray();
        Assert.assertArrayEquals(datagram5.array(), result);

        bos.reset();

        datagramBuffer.writeTo(channel);
        result = bos.toByteArray();
        Assert.assertArrayEquals(datagram0.array(), result);

        bos.reset();

        datagramBuffer.writeTo(channel);
        result = bos.toByteArray();
        Assert.assertArrayEquals(datagram3.array(), result);

        bos.reset();

        datagramBuffer.writeTo(channel);
        result = bos.toByteArray();
        Assert.assertArrayEquals(datagram4.array(), result);
    }

    @Test
    public void testCircular() throws IOException {
        ByteBuffer datagram5 = createDatagram(5);
        ByteBuffer datagram3 = createDatagram(3);

        DatagramBuffer datagramBuffer = new DatagramBuffer(14);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        WritableByteChannel channel = Channels.newChannel(bos);

        // write and consume 10 bytes
        datagramBuffer.readFrom(createDatagram(10));
        datagramBuffer.writeTo(Channels.newChannel(new ByteArrayOutputStream())); // forget

        datagramBuffer.readFrom(datagram5);
        datagramBuffer.readFrom(datagram3);

        // DatagramBuffer is expected to store the whole datagram (even if it exceeds its "capacity")
        datagramBuffer.writeTo(channel);
        byte[] result = bos.toByteArray();
        Assert.assertArrayEquals(datagram5.array(), result);

        bos.reset();

        datagramBuffer.writeTo(channel);
        result = bos.toByteArray();
        Assert.assertArrayEquals(datagram3.array(), result);
    }
}
