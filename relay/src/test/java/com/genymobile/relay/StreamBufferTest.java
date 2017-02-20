package com.genymobile.relay;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

public class StreamBufferTest {

    private ByteBuffer createChunk() {
        byte[] data = {0, 1, 2, 3, 4, 5};
        return ByteBuffer.wrap(data);
    }

    @Test
    public void testSimple() throws IOException {
        ByteBuffer buffer = createChunk();

        StreamBuffer streamBuffer = new StreamBuffer(9);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        WritableByteChannel channel = Channels.newChannel(bos);

        streamBuffer.readFrom(buffer);
        streamBuffer.writeTo(channel);

        byte[] result = bos.toByteArray();
        Assert.assertArrayEquals(buffer.array(), result);
    }

    @Test
    public void testCircular() throws IOException {
        ByteBuffer buffer = createChunk();

        StreamBuffer streamBuffer = new StreamBuffer(9);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        WritableByteChannel channel = Channels.newChannel(bos);

        // write and consume 6 bytes
        streamBuffer.readFrom(buffer);
        streamBuffer.writeTo(Channels.newChannel(new ByteArrayOutputStream())); // forget
        buffer.rewind();

        streamBuffer.readFrom(buffer);
        streamBuffer.writeTo(channel);

        // StreamBuffer is expected to break writes at circular buffer boundaries (capacity + 1)
        // This is not a requirement, but this verifies that the implementation works as expected
        byte[] result = bos.toByteArray();
        byte[] expected = {0, 1, 2, 3};
        Assert.assertArrayEquals(expected, result);

        // write the remaining
        streamBuffer.writeTo(channel);
        result = bos.toByteArray();
        Assert.assertArrayEquals(buffer.array(), result);
    }

    @Test
    public void testNotEnoughSpace() throws IOException {
        ByteBuffer buffer = createChunk();

        StreamBuffer streamBuffer = new StreamBuffer(9);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        WritableByteChannel channel = Channels.newChannel(bos);

        streamBuffer.readFrom(buffer);
        buffer.rewind();
        streamBuffer.readFrom(buffer);

        Assert.assertEquals(3, buffer.remaining());

        streamBuffer.writeTo(channel);

        byte[] result = bos.toByteArray();
        byte[] expected = {0, 1, 2, 3, 4, 5, 0, 1, 2};
        Assert.assertArrayEquals(expected, result);
    }
}
