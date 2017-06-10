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

import java.nio.ByteBuffer;

@SuppressWarnings("checkstyle:MagicNumber")
public class UDPHeaderTest {

    private static ByteBuffer createMockHeaders() {
        ByteBuffer buffer = ByteBuffer.allocate(8);

        buffer.putShort((short) 1234); // source port
        buffer.putShort((short) 5678); // destination port
        buffer.putShort((short) 42); // length
        buffer.putShort((short) 0); // checksum

        return buffer;
    }

    @Test
    public void testParsePacketHeaders() {
        UDPHeader header = new UDPHeader(createMockHeaders());
        Assert.assertNotNull("Valid UDP header not parsed", header);
        Assert.assertEquals(1234, header.getSourcePort());
        Assert.assertEquals(5678, header.getDestinationPort());
    }

    @Test
    public void testEditHeaders() {
        ByteBuffer buffer = createMockHeaders();
        UDPHeader header = new UDPHeader(buffer);

        header.setSourcePort(1111);
        header.setDestinationPort(2222);
        header.setPayloadLength(34);

        Assert.assertEquals(1111, header.getSourcePort());
        Assert.assertEquals(2222, header.getDestinationPort());

        // assert the buffer has been modified
        int sourcePort = Short.toUnsignedInt(buffer.getShort(0));
        int destinationPort = Short.toUnsignedInt(buffer.getShort(2));
        int length = Short.toUnsignedInt(buffer.getShort(4));

        Assert.assertEquals(1111, sourcePort);
        Assert.assertEquals(2222, destinationPort);
        Assert.assertEquals(42, length);

        header.swapSourceAndDestination();

        Assert.assertEquals(2222, header.getSourcePort());
        Assert.assertEquals(1111, header.getDestinationPort());

        sourcePort = Short.toUnsignedInt(buffer.getShort(0));
        destinationPort = Short.toUnsignedInt(buffer.getShort(2));

        Assert.assertEquals(2222, sourcePort);
        Assert.assertEquals(1111, destinationPort);
    }

    @Test
    public void testCopyTo() {
        ByteBuffer buffer = createMockHeaders();
        UDPHeader header = new UDPHeader(buffer);

        ByteBuffer target = ByteBuffer.allocate(32);
        target.position(12);
        UDPHeader copy = header.copyTo(target);
        copy.setSourcePort(9999);

        Assert.assertEquals(20, target.position());
        Assert.assertEquals("Header must modify target", 9999, target.getShort(12));
        Assert.assertEquals("Header must not modify buffer", 1234, buffer.getShort(0));
    }
}
