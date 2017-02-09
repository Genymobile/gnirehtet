package com.genymobile.relay;

public class IPv4PacketDeflaterTest {

//    private IPv4Packet createMockPacket() {
//        ByteBuffer buffer = ByteBuffer.allocate(32);
//
//        buffer.put((byte) ((4 << 4) | 5)); // versionAndIHL
//        buffer.put((byte) 0); // ToS
//        buffer.putShort((short) 32); // total length 20 + 8 + 4
//        buffer.putInt(0); // IdFlagsFragmentOffset
//        buffer.put((byte) 0); // TTL
//        buffer.put((byte) 17); // protocol (UDP)
//        buffer.putShort((short) 0); // checksum
//        buffer.putInt(0x12345678); // source address
//        buffer.putInt(0x42424242); // destination address
//
//        buffer.putShort((short) 1234); // source port
//        buffer.putShort((short) 5678); // destination port
//        buffer.putShort((short) 12); // length
//        buffer.putShort((short) 0); // checksum
//
//        buffer.putInt(0x11223344); // payload
//
//        buffer.flip();
//        return new IPv4Packet(buffer);
//    }
//
//    @Test
//    public void testNetworkDeflate() {
//        IPv4Packet packet = createMockPacket();
//
//        IPv4PacketDeflater deflater = new IPv4PacketDeflater(new NetworkPacketSerializer());
//        deflater.sendPacket(packet);
//
//        ByteBuffer buffer = ByteBuffer.allocate(128);
//
//        deflater.writeTo(buffer);
//        buffer.flip();
//
//        Assert.assertEquals(32, buffer.limit());
//
//        byte[] data = new byte[32];
//        buffer.get(data);
//
//        Assert.assertArrayEquals(packet.getRaw().array(), data);
//    }
//
//    @Test
//    public void testPayloadDeflate() {
//        IPv4Packet packet = createMockPacket();
//
//        PayloadPacketSerializer serializer = new PayloadPacketSerializer(packet.getIpv4Header(), packet.getTransportHeader());
//
//        IPv4PacketDeflater deflater = new IPv4PacketDeflater(serializer);
//        deflater.sendPacket(packet);
//
//        ByteBuffer buffer = ByteBuffer.allocate(128);
//
//        deflater.writeTo(buffer);
//        buffer.flip();
//
//        Assert.assertEquals(4, buffer.limit());
//
//        byte[] data = new byte[4];
//        buffer.get(data);
//
//        byte[] expected = new byte[4];
//        packet.getPayload().get(expected);
//
//        Assert.assertArrayEquals(expected, data);
//    }
}
