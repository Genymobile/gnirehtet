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

package com.genymobile.gnirehtet.relay;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

@SuppressWarnings("checkstyle:MagicNumber")
public class IPv4PacketBufferTest {

    private static ByteBuffer createMockPacket() {
        ByteBuffer buffer = ByteBuffer.allocate(32);
        writeMockPacketTo(buffer);
        buffer.flip();
        return buffer;
    }

    private static void writeMockPacketTo(ByteBuffer buffer) {
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

    private static ReadableByteChannel contentToChannel(ByteBuffer buffer) {
        ByteArrayInputStream bis = new ByteArrayInputStream(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.limit());
        return Channels.newChannel(bis);
    }

    @Test
    public void testParseIPv4PacketBuffer() throws IOException {
        ByteBuffer buffer = createMockPacket();

        IPv4PacketBuffer packetBuffer = new IPv4PacketBuffer();

        packetBuffer.readFrom(contentToChannel(buffer));

        IPv4Packet packet = packetBuffer.asIPv4Packet();
        Assert.assertNotNull(packet);

        checkPacketHeaders(packet);
    }

    @Test
    public void testParseFragmentedIPv4PacketBuffer() throws IOException {
        ByteBuffer buffer = createMockPacket();

        IPv4PacketBuffer packetBuffer = new IPv4PacketBuffer();

        // onReadable the first 14 bytes
        buffer.limit(14);
        packetBuffer.readFrom(contentToChannel(buffer));

        Assert.assertNull(packetBuffer.asIPv4Packet());

        // onReadable the remaining
        buffer.limit(32).position(14);
        packetBuffer.readFrom(contentToChannel(buffer));

        IPv4Packet packet = packetBuffer.asIPv4Packet();
        Assert.assertNotNull(packet);

        checkPacketHeaders(packet);
    }

    private static ByteBuffer createMockPackets() {
        ByteBuffer buffer = ByteBuffer.allocate(32 * 3);
        for (int i = 0; i < 3; ++i) {
            writeMockPacketTo(buffer);
        }
        buffer.flip();
        return buffer;
    }

    @Test
    public void testMultiPackets() throws IOException {
        ByteBuffer buffer = createMockPackets();

        IPv4PacketBuffer packetBuffer = new IPv4PacketBuffer();
        packetBuffer.readFrom(contentToChannel(buffer));

        for (int i = 0; i < 3; ++i) {
            IPv4Packet packet = packetBuffer.asIPv4Packet();
            Assert.assertNotNull(packet);
            checkPacketHeaders(packet);
            packetBuffer.next();
        }

        // after the 3 packets have been consumed, there is nothing left
        Assert.assertNull(packetBuffer.asIPv4Packet());
    }

    private static void checkPacketHeaders(IPv4Packet packet) {
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
