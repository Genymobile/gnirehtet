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
        return (head + 1) % data.length == tail;
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
            wrapper.limit(head).position(tail);
            int w = channel.write(wrapper);
            tail = wrapper.position();
            optimize();
            return w;
        }

        if (head < tail) {
            wrapper.limit(data.length).position(tail);
            int w = channel.write(wrapper);
            tail = wrapper.position() % data.length;
            optimize();
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

    /**
     * To avoid unnecessary copies, StreamBuffer writes at most until the "end"
     * of the circular buffer, which is subobtimal (it could have written more
     * data if they have been contiguous).
     * <p>
     * In order to minimize the occurrence of this event, reset the head and
     * tail to 0 when the buffer is empty (no copy is involved).
     * <p>
     * This is especially useful when the StreamBuffer is used to read/write
     * one packet at a time, so the "end" of the buffer is guaranteed to never
     * be reached.
     */
    private void optimize() {
        if (isEmpty()) {
            head = 0;
            tail = 0;
        }
    }
}
