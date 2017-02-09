package com.genymobile.relay;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;

public class UDPHeaderTest {

    private ByteBuffer createMockHeaders() {
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

        header.switchSourceAndDestination();

        Assert.assertEquals(2222, header.getSourcePort());
        Assert.assertEquals(1111, header.getDestinationPort());

        sourcePort = Short.toUnsignedInt(buffer.getShort(0));
        destinationPort = Short.toUnsignedInt(buffer.getShort(2));

        Assert.assertEquals(2222, sourcePort);
        Assert.assertEquals(1111, destinationPort);
    }
}