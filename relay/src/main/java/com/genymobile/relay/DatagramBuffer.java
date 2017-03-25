/*
 * Copyright (C) 2017 Genymobile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.genymobile.relay;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * Circular buffer to store datagrams (preserving their boundaries).
 * <p>
 * <pre>
 *     circularBufferLength
 * |<------------------------->| extra space for storing the last datagram in one block
 * +---------------------------+------+
 * |                           |      |
 * |[D4]     [  D1  ][ D2 ][  D3  ]   |
 * +---------------------------+------+
 *     ^     ^
 *  head     tail
 * </pre>
 */
public class DatagramBuffer {

    private static final String TAG = DatagramBuffer.class.getSimpleName();

    // every datagram is stored along with a header storing its length, on 16 bits
    private static final int HEADER_LENGTH = 2;
    private static final int MAX_DATAGRAM_LENGTH_LOG = 16;
    private static final int MAX_DATAGRAM_LENGTH = 1 << MAX_DATAGRAM_LENGTH_LOG;
    private static final int MAX_BLOCK_LENGTH = HEADER_LENGTH + MAX_DATAGRAM_LENGTH;

    private static final int MASK_8_LOWEST_BITS = 0xff;
    private static final int MASK_16_LOWEST_BITS = 0xffff;
    private static final int MASK_ALL_EXCEPT_16_LOWEST_BITS = ~MASK_16_LOWEST_BITS;
    private static final int ONE_BYTE_IN_BITS = 8;

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
            throw new IllegalArgumentException("Datagram length (" + buffer.remaining() + ") may not be greater than "
                    + MAX_DATAGRAM_LENGTH + " bytes");
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
        assert (length & MASK_ALL_EXCEPT_16_LOWEST_BITS) == 0 : "Length must be stored on 16 bits";
        data[head++] = (byte) ((length >> ONE_BYTE_IN_BITS) & MASK_8_LOWEST_BITS);
        data[head++] = (byte) (length & MASK_8_LOWEST_BITS);
    }

    private int readLength() {
        int length = ((data[tail] & MASK_8_LOWEST_BITS) << ONE_BYTE_IN_BITS) | (data[tail + 1] & MASK_8_LOWEST_BITS);
        tail += 2;
        return length;
    }
}
