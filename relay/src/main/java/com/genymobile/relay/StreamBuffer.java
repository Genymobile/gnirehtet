package com.genymobile.relay;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public class StreamBuffer {

    private final byte[] data;
    private final ByteBuffer wrapper;
    private int head;
    private int tail;

    public StreamBuffer(int capacity) {
        data = new byte[capacity + 1];
        wrapper = ByteBuffer.wrap(data);
    }

    public boolean isEmpty() {
        return head == tail;
    }

    public boolean isFull() {
        return head + 1 == tail;
    }

    public int size() {
        if (head < tail) {
            return head + data.length - tail;
        }
        return head - tail;
    }

    public int capacity() {
        return data.length - 1;
    }

    public int remaining() {
        return capacity() - size();
    }

    public int writeTo(WritableByteChannel channel) throws IOException {
        if (head > tail) {
            wrapper.position(tail).limit(head);
            int w = channel.write(wrapper);
            tail = wrapper.position();
            return w;
        }

        if (head < tail) {
            wrapper.position(tail).limit(data.length);
            int w = channel.write(wrapper);
            tail = wrapper.position() % data.length;
            return w;
        }

        // else head == tail, which means empty buffer, nothing to do
        return 0;
    }

    public void readFrom(ByteBuffer buffer) {
        int requested = Math.min(buffer.remaining(), remaining());
        if (requested <= data.length - head) {
            buffer.get(data, head, requested);
        } else {
            buffer.get(data, head, data.length - head);
            buffer.get(data, 0, head + requested - data.length);
        }
        head = (head + requested) % data.length;
    }
}
