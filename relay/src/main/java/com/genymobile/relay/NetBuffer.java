package com.genymobile.relay;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

public class NetBuffer {

    private static final ByteBuffer buffer = ByteBuffer.allocate(IPv4Packet.MAX_PACKET_LENGTH);

    private final Queue<ByteBuffer> queue = new LinkedList<>();

    private final int capacity;

    public NetBuffer(int capacity) {
        this.capacity = capacity;
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public boolean isFull() {
        return queue.size() >= capacity;
    }

    public boolean offer(ByteBuffer buffer) {
        if (isFull()) {
            return false;
        }
        return queue.offer(buffer);
    }

    public ByteBuffer poll() {
        return queue.poll();
    }

    public boolean writeOne(WritableByteChannel channel) throws IOException {
        ByteBuffer buffer = poll();
        int remaining = buffer.remaining();
        int w = channel.write(buffer);
        if (w == -1) {
            return false;
        }
        if (w != remaining) {
            throw new IOException("Channel unexpectedly breaks packet boundaries");
        }
        return true;
    }

    public boolean write(GatheringByteChannel channel) throws IOException {
        if (channel.write(toArray()) == -1) {
            return false;
        }
        compact();
        return true;
    }

    public boolean read(ReadableByteChannel channel) throws IOException {
        buffer.clear();
        int r = channel.read(buffer);
        if (r == -1) {
            return false;
        }
        buffer.flip();
        offer(Binary.copy(buffer));
        return true;
    }

    private ByteBuffer[] toArray() {
        return queue.toArray(new ByteBuffer[queue.size()]);
    }

    private void compact() {
        Iterator<ByteBuffer> iterator = queue.iterator();
        while (iterator.hasNext()) {
            ByteBuffer buffer = iterator.next();
            if (!buffer.hasRemaining()) {
                iterator.remove();
            }
        }
    }
}
