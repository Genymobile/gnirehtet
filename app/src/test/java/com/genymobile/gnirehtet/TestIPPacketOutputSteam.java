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

package com.genymobile.gnirehtet;

import junit.framework.Assert;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

@SuppressWarnings("checkstyle:MagicNumber")
public class TestIPPacketOutputSteam {

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
    public void testSimplePacket() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IPPacketOutputStream pos = new IPPacketOutputStream(bos);

        byte[] rawPacket = createMockPacket().array();

        pos.write(rawPacket, 0, 14);
        Assert.assertEquals("Partial packet should not be written", 0, bos.size());

        pos.write(rawPacket, 14, 14);
        Assert.assertEquals("Partial packet should not be written", 0, bos.size());

        pos.write(rawPacket, 28, 4);
        Assert.assertEquals("Complete packet should be written", 32, bos.size());

        byte[] result = bos.toByteArray();
        Assert.assertTrue("Resulting array must be identical", Arrays.equals(rawPacket, result));
    }

    @Test
    public void testSeveralPacketsAtOnce() throws IOException {
        class CapturingOutputStream extends ByteArrayOutputStream {
            private int packetCount;

            @Override
            public void write(byte[] b, int off, int len) {
                super.write(b, off, len);
                ++packetCount;
            }
        }
        CapturingOutputStream cos = new CapturingOutputStream();
        IPPacketOutputStream pos = new IPPacketOutputStream(cos);

        ByteBuffer buffer = ByteBuffer.allocate(3 * 32);
        for (int i = 0; i < 3; ++i) {
            writeMockPacketTo(buffer);
        }
        byte[] rawPackets = buffer.array();

        pos.write(rawPackets, 0, 70); // 2 packets + 6 bytes
        Assert.assertEquals("Exactly 2 packets should have been written", 64, cos.size());
        Assert.assertEquals("Packets should be written individually to the target", 2, cos.packetCount);

        pos.write(rawPackets, 70, 26);
        Assert.assertEquals("Exactly 3 packets should have been written", 96, cos.size());
        Assert.assertEquals("Packets should be written individually to the target", 3, cos.packetCount);
    }
}
