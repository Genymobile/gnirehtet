package com.genymobile.relay;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;

public class TCPHeaderTest {

    private ByteBuffer createMockPacket() {
        ByteBuffer buffer = ByteBuffer.allocate(64);

        buffer.put((byte) ((4 << 4) | 5)); // versionAndIHL
        buffer.put((byte) 0); // ToS
        buffer.putShort((short) 44); // total length 20 + 20 + 4
        buffer.putInt(0); // IdFlagsFragmentOffset
        buffer.put((byte) 0); // TTL
        buffer.put((byte) 6); // protocol (TCP)
        buffer.putShort((short) 0); // checksum
        buffer.putInt(0x12345678); // source address
        buffer.putInt(0xa2a24242); // destination address

        buffer.putShort((short) 0x1234); // source port
        buffer.putShort((short) 0x5678); // destination port
        buffer.putInt(0x111); // sequence number
        buffer.putInt(0x222); // acknowledgment number
        buffer.putShort((short) (5 << 12)); // data offset + flags(0)
        buffer.putShort((short) 0); // window (don't care for these tests)
        buffer.putShort((short) 0); // checksum
        buffer.putShort((short) 0); // urgent pointer

        buffer.putInt(0x11223344); // payload

        return buffer;
    }

    private ByteBuffer createMockOddPacket() {
        ByteBuffer buffer = ByteBuffer.allocate(64);

        buffer.put((byte) ((4 << 4) | 5)); // versionAndIHL
        buffer.put((byte) 0); // ToS
        buffer.putShort((short) 45); // total length 20 + 20 + 5
        buffer.putInt(0); // IdFlagsFragmentOffset
        buffer.put((byte) 0); // TTL
        buffer.put((byte) 6); // protocol (TCP)
        buffer.putShort((short) 0); // checksum
        buffer.putInt(0x12345678); // source address
        buffer.putInt(0xa2a24242); // destination address

        buffer.putShort((short) 0x1234); // source port
        buffer.putShort((short) 0x5678); // destination port
        buffer.putInt(0x111); // sequence number
        buffer.putInt(0x222); // acknowledgment number
        buffer.putShort((short) (5 << 12)); // data offset + flags(0)
        buffer.putShort((short) 0); // window (don't care for these tests)
        buffer.putShort((short) 0); // checksum
        buffer.putShort((short) 0); // urgent pointer

        // payload
        buffer.putInt(0x11223344);
        buffer.put((byte) 0x55);

        return buffer;
    }

    private ByteBuffer createMockTCPHeader() {
        ByteBuffer buffer = ByteBuffer.allocate(20);

        buffer.putShort((short) 0x1234); // source port
        buffer.putShort((short) 0x5678); // destination port
        buffer.putInt(0x111); // sequence number
        buffer.putInt(0x222); // acknowledgment number
        buffer.putShort((short) (5 << 12)); // data offset + flags(0)
        buffer.putShort((short) 0); // window (don't care for these tests)
        buffer.putShort((short) 0); // checksum
        buffer.putShort((short) 0); // urgent pointer

        buffer.flip();
        return buffer;
    }

    @Test
    public void testEditHeaders() {
        ByteBuffer buffer = createMockTCPHeader();
        TCPHeader header = new TCPHeader(buffer);

        header.setSourcePort(1111);
        header.setDestinationPort(2222);
        header.setSequenceNumber(300);
        header.setAcknowledgementNumber(101);
        header.setFlags(TCPHeader.FLAG_FIN | TCPHeader.FLAG_ACK);

        Assert.assertEquals(1111, header.getSourcePort());
        Assert.assertEquals(2222, header.getDestinationPort());
        Assert.assertEquals(300, header.getSequenceNumber());
        Assert.assertEquals(101, header.getAcknowledgementNumber());
        Assert.assertEquals(TCPHeader.FLAG_FIN | TCPHeader.FLAG_ACK, header.getFlags());

        // assert the buffer has been modified
        int sourcePort = Short.toUnsignedInt(buffer.getShort(0));
        int destinationPort = Short.toUnsignedInt(buffer.getShort(2));
        int sequenceNumber = buffer.getInt(4);
        int acknowledgementNumber = buffer.getInt(8);
        short dataOffsetAndFlags = buffer.getShort(12);

        Assert.assertEquals(1111, sourcePort);
        Assert.assertEquals(2222, destinationPort);
        Assert.assertEquals(300, sequenceNumber);
        Assert.assertEquals(101, acknowledgementNumber);
        Assert.assertEquals(0x5011, dataOffsetAndFlags);
    }

    @Test
    public void testComputeChecksum() {
        ByteBuffer buffer = createMockPacket();
        buffer.flip();
        IPv4Packet packet = new IPv4Packet(buffer);
        TCPHeader tcpHeader = (TCPHeader) packet.getTransportHeader();

        // set a fake checksum value to assert that it is correctly computed
        buffer.putShort(36, (short) 0x79);

        tcpHeader.computeChecksum(packet.getIpv4Header(), packet.getPayload());

        // pseudo-header
        int sum = 0x1234 + 0x5678 + 0xa2a2 + 0x4242 + 0x0006 + 0x0018;

        // header
        sum += 0x1234 + 0x5678 + 0x0000 + 0x0111 + 0x0000 + 0x0222 + 0x5000 + 0x0000 + 0x0000 + 0x0000;

        // payload
        sum += 0x1122 + 0x3344;

        while ((sum & ~0xffff) != 0) {
            sum = (sum & 0xffff) + (sum >> 16);
        }
        short checksum = (short) ~sum;

        Assert.assertEquals(checksum, tcpHeader.getChecksum());
    }

    @Test
    public void testComputeChecksumOddLength() {
        ByteBuffer buffer = createMockOddPacket();
        buffer.flip();

        IPv4Packet packet = new IPv4Packet(buffer);
        TCPHeader tcpHeader = (TCPHeader) packet.getTransportHeader();

        // set a fake checksum value to assert that it is correctly computed
        buffer.putShort(36, (short) 0x79);

        tcpHeader.computeChecksum(packet.getIpv4Header(), packet.getPayload());

        // pseudo-header
        int sum = 0x1234 + 0x5678 + 0xa2a2 + 0x4242 + 0x0006 + 0x0019;

        // header
        sum += 0x1234 + 0x5678 + 0x0000 + 0x0111 + 0x0000 + 0x0222 + 0x5000 + 0x0000 + 0x0000 + 0x0000;

        // payload
        sum += 0x1122 + 0x3344 + 0x5500;

        while ((sum & ~0xffff) != 0) {
            sum = (sum & 0xffff) + (sum >> 16);
        }
        short checksum = (short) ~sum;

        Assert.assertEquals(checksum, tcpHeader.getChecksum());
    }

    @Test
    public void testCopyTo() {
        ByteBuffer buffer = createMockTCPHeader();
        TCPHeader header = new TCPHeader(buffer);

        ByteBuffer target = ByteBuffer.allocate(40);
        target.position(12);
        TCPHeader copy = header.copyTo(target);
        copy.setSourcePort(9999);

        Assert.assertEquals(32, target.position());
        Assert.assertEquals("Header must modify target", 9999, target.getShort(12));
        Assert.assertEquals("Header must not modify buffer", 0x1234, buffer.getShort(0));

    }
}
