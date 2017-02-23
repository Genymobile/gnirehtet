package com.genymobile.relay;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;

public class UDPPacketBuilderTest {

    private static ByteBuffer createMockPacket() {
        ByteBuffer buffer = ByteBuffer.allocate(32);

        buffer.put((byte) ((4 << 4) | 5)); // versionAndIHL
        buffer.put((byte) 0); // ToS
        buffer.putShort((short) 32); // total length 20 + 8 + 4
        buffer.putInt(0); // IdFlagsFragmentOffset
        buffer.put((byte) 0); // TTL
        buffer.put((byte) 17); // protocol (UDP)
        buffer.putShort((short) 0); // checksum
        buffer.putInt(0x12345678); // source address
        buffer.putInt(0x42424242); // destination address

        buffer.putShort((short) 1234); // source port
        buffer.putShort((short) 5678); // destination port
        buffer.putShort((short) 4); // length
        buffer.putShort((short) 0); // checksum

        buffer.putInt(0x11223344); // payload

        return buffer;
    }

    @Test
    public void testBuildDatagram() throws IOException {
        IPv4Packet referencePacket = new IPv4Packet(createMockPacket());
        IPv4Header ipv4Header = referencePacket.getIpv4Header();
        UDPHeader udpHeader = (UDPHeader) referencePacket.getTransportHeader();
        UDPPacketBuilder builder = new UDPPacketBuilder(ipv4Header, udpHeader);

        Assert.assertFalse(builder.hasPending());

        byte[] data = { 0, 1, 2, 3, 4};
        builder.readFrom(Channels.newChannel(new ByteArrayInputStream(data)));

        Assert.assertTrue(builder.hasPending());

        IPv4Packet packet = builder.getPacket();
        Assert.assertEquals(33, packet.getRawLength());
        Assert.assertEquals(5, packet.getPayloadLength());

        ByteBuffer payload = packet.getPayload();
        byte[] result = new byte[5];
        payload.get(result);
        Assert.assertArrayEquals(data, result);
    }
}
