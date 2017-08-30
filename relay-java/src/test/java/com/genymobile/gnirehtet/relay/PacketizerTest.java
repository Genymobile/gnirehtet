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
public class PacketizerTest {

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
    public void testMergeHeadersAndPayload() throws IOException {
        IPv4Packet referencePacket = new IPv4Packet(createMockPacket());
        IPv4Header ipv4Header = referencePacket.getIpv4Header();
        TransportHeader transportHeader = referencePacket.getTransportHeader();

        byte[] data = {0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88};
        ReadableByteChannel channel = Channels.newChannel(new ByteArrayInputStream(data));

        Packetizer packetizer = new Packetizer(ipv4Header, transportHeader);
        IPv4Packet packet = packetizer.packetize(channel);
        Assert.assertEquals(36, packet.getIpv4Header().getTotalLength());

        ByteBuffer packetPayload = packet.getPayload();
        Assert.assertEquals(8, packetPayload.remaining());
        Assert.assertEquals(0x1122334455667788L, packetPayload.getLong());
    }

    @Test
    public void testPacketizeChunks() throws IOException {
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
