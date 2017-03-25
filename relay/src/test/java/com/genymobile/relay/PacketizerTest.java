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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class PacketizerTest {

    private static final int IPV4_VERSION = 4;
    private static final int IPV4_IHL_OFFSET = 4;
    private static final int IPV4_MOCK_IHL_VALUE = 5;
    private static final int IPV4_MOCK_PROTOCOL = IPv4Header.Protocol.UDP.getNumber();
    private static final int IPV4_MOCK_SOURCE_ADDRESS = 0x12345678;
    private static final int IPV4_MOCK_DESTINATION_ADDRESS = 0x42424242;
    private static final int IPV4_MOCK_SOURCE_PORT = 1234;
    private static final int IPV4_MOCK_DESTINATION_PORT = 5678;
    private static final int IPV4_MOCK_PAYLOAD_LENGTH = 4;
    private static final int IPV4_MOCK_PAYLOAD = 0x11223344;
    private static final int IPV4_MOCK_2_PAYLOAD_LENGTH = 8;
    private static final long IPV4_MOCK_2_PAYLOAD = 0x1122334455667788L;
    private static final int IPV4_MOCK_2_EXPECTED_HEADER_LENGTH = 36;
    private static final int IPV4_MOCK_2_EXPECTED_REMAINING_BYTES = 8;
    private static final int IPV4_PACKETIZE_FIRST_CHUNK_LENGTH = 2;
    private static final int IPV4_PACKETIZE_SECOND_CHUNK_LENGTH = 3;
    private static final int IPV4_MOCK_2_EXPECTED_HEADER_LENGTH_WHEN_PACKETIZED_WITH_CHUNK_LENGTH_2 = 30;
    private static final int IPV4_MOCK_2_EXPECTED_HEADER_LENGTH_WHEN_PACKETIZED_WITH_CHUNK_LENGTH_3 = 31;
    private static final int IPV4_MOCK_PAYLOAD_2_FIRST_BYTES = 0x1122;
    private static final int IPV4_MOCK_2_PAYLOAD_BYTE_5 = 0x33;
    private static final int IPV4_MOCK_2_PAYLOAD_BYTE_6 = 0x44;
    private static final int IPV4_MOCK_2_PAYLOAD_BYTE_7 = 0x55;

    private static ByteBuffer createMockPacket() {
        ByteBuffer buffer = ByteBuffer.allocate(32);

        buffer.put((byte) ((IPV4_VERSION << IPV4_IHL_OFFSET) | IPV4_MOCK_IHL_VALUE)); // versionAndIHL
        buffer.put((byte) 0); // ToS
        buffer.putShort((short) 32); // total length 20 + 8 + 4
        buffer.putInt(0); // IdFlagsFragmentOffset
        buffer.put((byte) 0); // TTL
        buffer.put((byte) IPV4_MOCK_PROTOCOL); // protocol (UDP)
        buffer.putShort((short) 0); // checksum
        buffer.putInt(IPV4_MOCK_SOURCE_ADDRESS); // source address
        buffer.putInt(IPV4_MOCK_DESTINATION_ADDRESS); // destination address

        buffer.putShort((short) IPV4_MOCK_SOURCE_PORT); // source port
        buffer.putShort((short) IPV4_MOCK_DESTINATION_PORT); // destination port
        buffer.putShort((short) IPV4_MOCK_PAYLOAD_LENGTH); // length
        buffer.putShort((short) 0); // checksum

        buffer.putInt(IPV4_MOCK_PAYLOAD); // payload

        return buffer;
    }

    @Test
    public void testMergeHeadersAndPayload() {
        IPv4Packet referencePacket = new IPv4Packet(createMockPacket());
        IPv4Header ipv4Header = referencePacket.getIpv4Header();
        TransportHeader transportHeader = referencePacket.getTransportHeader();

        ByteBuffer payload = ByteBuffer.allocate(IPV4_MOCK_2_PAYLOAD_LENGTH);
        payload.putLong(IPV4_MOCK_2_PAYLOAD);
        payload.flip();

        Packetizer packetizer = new Packetizer(ipv4Header, transportHeader);
        IPv4Packet packet = packetizer.packetize(payload);
        Assert.assertEquals(IPV4_MOCK_2_EXPECTED_HEADER_LENGTH, packet.getIpv4Header().getTotalLength());

        ByteBuffer packetPayload = packet.getPayload();
        Assert.assertEquals(IPV4_MOCK_2_EXPECTED_REMAINING_BYTES, packetPayload.remaining());
        Assert.assertEquals(IPV4_MOCK_2_PAYLOAD, packetPayload.getLong());
    }

    @Test
    public void testPacketizeChunksFromByteBuffer() {
        IPv4Packet originalPacket = new IPv4Packet(createMockPacket());
        IPv4Header ipv4Header = originalPacket.getIpv4Header();
        TransportHeader transportHeader = originalPacket.getTransportHeader();

        ByteBuffer payload = ByteBuffer.allocate(IPV4_MOCK_2_PAYLOAD_LENGTH);
        payload.putLong(IPV4_MOCK_2_PAYLOAD);
        payload.flip();

        Packetizer packetizer = new Packetizer(ipv4Header, transportHeader);

        IPv4Packet packet = packetizer.packetize(payload, IPV4_PACKETIZE_FIRST_CHUNK_LENGTH);
        ByteBuffer packetPayload = packet.getPayload();

        Assert.assertEquals(IPV4_MOCK_2_EXPECTED_HEADER_LENGTH_WHEN_PACKETIZED_WITH_CHUNK_LENGTH_2, packet.getIpv4Header().getTotalLength());
        Assert.assertEquals(2, packetPayload.remaining());
        Assert.assertEquals(IPV4_MOCK_PAYLOAD_2_FIRST_BYTES, Short.toUnsignedInt(packetPayload.getShort()));

        packet = packetizer.packetize(payload, IPV4_PACKETIZE_SECOND_CHUNK_LENGTH);
        packetPayload = packet.getPayload();
        Assert.assertEquals(IPV4_MOCK_2_EXPECTED_HEADER_LENGTH_WHEN_PACKETIZED_WITH_CHUNK_LENGTH_3, packet.getIpv4Header().getTotalLength());
        Assert.assertEquals(IPV4_MOCK_2_PAYLOAD_LENGTH - IPV4_PACKETIZE_FIRST_CHUNK_LENGTH - IPV4_PACKETIZE_SECOND_CHUNK_LENGTH, packetPayload.remaining());
        Assert.assertEquals(IPV4_MOCK_2_PAYLOAD_BYTE_5, packetPayload.get());
        Assert.assertEquals(IPV4_MOCK_2_PAYLOAD_BYTE_6, packetPayload.get());
        Assert.assertEquals(IPV4_MOCK_2_PAYLOAD_BYTE_7, packetPayload.get());

        packet = packetizer.packetize(payload, 1024);
        packetPayload = packet.getPayload();
        Assert.assertEquals(31, packet.getIpv4Header().getTotalLength());
        Assert.assertEquals(3, packetPayload.remaining());
        Assert.assertEquals(0x66, packetPayload.get());
        Assert.assertEquals(0x77, packetPayload.get());
        Assert.assertEquals((byte) 0x88, packetPayload.get());
    }

    @Test
    public void testPacketizeChunksFromChannel() throws IOException {
        IPv4Packet originalPacket = new IPv4Packet(createMockPacket());
        IPv4Header ipv4Header = originalPacket.getIpv4Header();
        TransportHeader transportHeader = originalPacket.getTransportHeader();

        byte[] data = {0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88};
        ReadableByteChannel channel = Channels.newChannel(new ByteArrayInputStream(data));

        Packetizer packetizer = new Packetizer(ipv4Header, transportHeader);

        IPv4Packet packet = packetizer.packetize(channel, 2);
        ByteBuffer packetPayload = packet.getPayload();


        Assert.assertEquals(30, packet.getIpv4Header().getTotalLength());
        Assert.assertEquals(2, packetPayload.remaining());
        Assert.assertEquals(0x1122, Short.toUnsignedInt(packetPayload.getShort()));

        packet = packetizer.packetize(channel, 3);
        packetPayload = packet.getPayload();
        Assert.assertEquals(31, packet.getIpv4Header().getTotalLength());
        Assert.assertEquals(3, packetPayload.remaining());
        Assert.assertEquals(0x33, packetPayload.get());
        Assert.assertEquals(0x44, packetPayload.get());
        Assert.assertEquals(0x55, packetPayload.get());

        packet = packetizer.packetize(channel, 1024);
        packetPayload = packet.getPayload();
        Assert.assertEquals(31, packet.getIpv4Header().getTotalLength());
        Assert.assertEquals(3, packetPayload.remaining());
        Assert.assertEquals(0x66, packetPayload.get());
        Assert.assertEquals(0x77, packetPayload.get());
        Assert.assertEquals((byte) 0x88, packetPayload.get());
    }
}
