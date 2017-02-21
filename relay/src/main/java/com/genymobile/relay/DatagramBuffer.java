package com.genymobile.relay;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * <pre>
 *     circularBufferLength
 * |<------------------------->| extra space for storing the last datagram in one block
 * +---------------------------+------+
 * |                           |      |
 * |[B4]     [  B1  ][ B2 ][  B3  ]   |
 * +---------------------------+------+
 *     ^     ^
 *  head     tail
 * </pre>
 */
public class DatagramBuffer {

    private static final String TAG = DatagramBuffer.class.getName();

    // every datagram is stored along with a header storing its length, on 16 bits
    private static final int HEADER_LENGTH = 2;
    private static final int MAX_DATAGRAM_LENGTH = 1 << 16;
    private static final int MAX_BLOCK_LENGTH = HEADER_LENGTH + MAX_DATAGRAM_LENGTH;

    private final byte[] data;
    private final ByteBuffer wrapper;
    private int head;
    private int tail;
    private final int circularBufferLength;

    public DatagramBuffer(int capacity) {
        data = new byte[capacity + MAX_BLOCK_LENGTH];
        wrapper = ByteBuffer.wrap(data);
        circularBufferLength = capacity + 1;
    }

    public boolean isEmpty() {
        return head == tail;
    }

    public boolean hasEnoughSpaceFor(int datagramLength) {
        if (head >= tail) {
            // there is at leat the extra space for storing 1 packet
            return true;
        }
        int remaining = tail - head - 1;
        return HEADER_LENGTH + datagramLength < remaining;
    }

    public int capacity() {
        return circularBufferLength - 1;
    }

    public boolean writeTo(WritableByteChannel channel) throws IOException {
        int length = readLength();
        wrapper.limit(tail + length).position(tail);
        tail += length;
        if (tail >= circularBufferLength) {
            tail = 0;
        }
        int w = channel.write(wrapper);
        if (w != length) {
            Log.e(TAG, "Cannot write the whole datagram to the channel (only " + w + "/" + length + ")");
            return false;
        }
        return true;
    }

    public boolean readFrom(ByteBuffer buffer) {
        int length = buffer.remaining();
        if (length > MAX_DATAGRAM_LENGTH) {
            throw new IllegalArgumentException("Datagram length (" + buffer.remaining() + ") may not be greater than " + MAX_DATAGRAM_LENGTH + " bytes");
        }
        if (!hasEnoughSpaceFor(length)) {
            return false;
        }
        writeLength(length);
        buffer.get(data, head, length);
        head += length;
        if (head >= circularBufferLength) {
            head = 0;
        }
        return true;
    }

    private void writeLength(int length) {
        assert (length & ~0xffff) == 0 : "Length must be stored on 16 bits";
        data[head++] = (byte) ((length >> 8) & 0xff);
        data[head++] = (byte) (length & 0xff);
    }

    private int readLength() {
        int length = ((data[tail] & 0xff) << 8) | (data[tail + 1] & 0xff);
        tail += 2;
        return length;
    }
}
