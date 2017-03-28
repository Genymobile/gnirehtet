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

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

@SuppressWarnings("checkstyle:MagicNumber")
public class StreamBufferTest {

    private static ByteBuffer createChunk() {
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

    static class DevNullChannel implements ByteChannel {

        private int writeChunkSize;

        DevNullChannel(int writeChunkSize) {
            this.writeChunkSize = writeChunkSize;
        }

        @Override
        public int read(ByteBuffer byteBuffer) {
            return 0;
        }

        @Override
        public int write(ByteBuffer byteBuffer) {
            int consume = Math.min(writeChunkSize, byteBuffer.remaining());
            byteBuffer.position(byteBuffer.position() + consume);
            return consume;
        }

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public void close() {
            // do nothing
        }
    }

    @Test
    public void testCircular() throws IOException {
        ByteBuffer buffer = createChunk();

        StreamBuffer streamBuffer = new StreamBuffer(9);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        WritableByteChannel channel = Channels.newChannel(bos);

        // put 6 bytes
        streamBuffer.readFrom(buffer);
        // consume 3 bytes
        streamBuffer.writeTo(new DevNullChannel(3));

        // put test data
        buffer.rewind();
        streamBuffer.readFrom(buffer);

        // consume 3 bytes (so that the first 6 bytes are totally consumed, and the "tail" position is 6)
        streamBuffer.writeTo(new DevNullChannel(3));

        // consume test data
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
