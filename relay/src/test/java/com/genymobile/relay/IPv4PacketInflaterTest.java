package com.genymobile.relay;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;

public class IPv4PacketInflaterTest {

    private ByteBuffer createMockPacket() {
        ByteBuffer buffer = ByteBuffer.allocate(32);
        writeMockPacketTo(buffer);
        buffer.flip();
        return buffer;
    }

    private void writeMockPacketTo(ByteBuffer buffer) {
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
        buffer.putShort((short) 12); // length
        buffer.putShort((short) 0); // checksum

        buffer.putInt(0x11223344); // payload
    }

    @Test
    public void testNetworkInflate() {
        ByteBuffer buffer = createMockPacket();

        IPv4PacketInflater inflater = new IPv4PacketInflater();
        inflater.readFrom(buffer);

        IPv4Packet packet = inflater.inflateNext();
        Assert.assertNotNull(packet);

        checkPacketHeaders(packet);
    }

//    @Test
//    public void testPayloadInflate() {
//        IPv4Packet packet = new IPv4Packet(createMockPacket());
//        PayloadPacketSerializer serializer = new PayloadPacketSerializer(packet.getIpv4Header(), packet.getTransportHeader());
//        IPv4PacketInflater inflater = new IPv4PacketInflater();
//
//        ByteBuffer buffer = ByteBuffer.allocate(32);
//        buffer.putInt(0x55667788);
//        buffer.flip();
//        inflater.readFrom(buffer);
//
//        IPv4Packet inflated = inflater.inflateNext();
//        Assert.assertNotNull(inflated);
//
//        checkPacketHeaders(inflated);
//
//        buffer.clear();
//        buffer.putLong(0x1234567890123456L);
//        buffer.flip();
//        inflater.readFrom(buffer);
//
//        inflated = inflater.inflateNext();
//        Assert.assertNotNull(inflated);
//
//        Assert.assertEquals(36, inflated.getIpv4Header().getTotalLength());
//    }

    @Test
    public void testFragmentedInflate() {
        ByteBuffer buffer = createMockPacket();

        IPv4PacketInflater inflater = new IPv4PacketInflater();

        // onReadable the first 14 bytes
        buffer.limit(14);
        inflater.readFrom(buffer);

        Assert.assertNull(inflater.inflateNext());

        // onReadable the remaining
        buffer.limit(32);
        inflater.readFrom(buffer);

        IPv4Packet packet = inflater.inflateNext();
        Assert.assertNotNull(packet);

        checkPacketHeaders(packet);
    }

    private ByteBuffer createMockPackets() {
        ByteBuffer buffer = ByteBuffer.allocate(32 * 3);
        for (int i = 0; i < 3; ++i)
            writeMockPacketTo(buffer);
        buffer.flip();
        return buffer;
    }

    @Test
    public void testMultiPackets() {
        ByteBuffer buffer = createMockPackets();

        IPv4PacketInflater inflater = new IPv4PacketInflater();
        inflater.readFrom(buffer);

        for (int i = 0; i < 3; ++i) {
            IPv4Packet packet = inflater.inflateNext();
            Assert.assertNotNull(packet);
            checkPacketHeaders(packet);
        }
    }

    private void checkPacketHeaders(IPv4Packet packet) {
        IPv4Header ipv4Header = packet.getIpv4Header();
        Assert.assertEquals(20, ipv4Header.getHeaderLength());
        Assert.assertEquals(32, ipv4Header.getTotalLength());
        Assert.assertEquals(IPv4Header.Protocol.UDP, ipv4Header.getProtocol());
        Assert.assertEquals(0x12345678, ipv4Header.getSource());
        Assert.assertEquals(0x42424242, ipv4Header.getDestination());

        UDPHeader udpHeader = (UDPHeader) packet.getTransportHeader();
        Assert.assertEquals(8, udpHeader.getHeaderLength());
        Assert.assertEquals(1234, udpHeader.getSourcePort());
        Assert.assertEquals(5678, udpHeader.getDestinationPort());
    }
}
