package com.genymobile.relay;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * Input: ByteBuffer containing raw IP packets
 * Output: IPv4Packet
 */
public class IPv4PacketInflater {

    private final ByteBuffer buffer = ByteBuffer.allocate(IPv4Packet.MAX_PACKET_LENGTH);

    public void readFrom(ByteBuffer input) {
        buffer.put(input);
    }

    public int readFrom(ReadableByteChannel channel) throws IOException {
        return channel.read(buffer);
    }

    public IPv4Packet inflateNext() {
        buffer.flip();
        IPv4Packet packet = parse(buffer);
        buffer.compact();
        return packet;
    }

    /**
     * Inflate an {@link IPv4Packet} from the data at the start of the {@code buffer}.
     * <p>
     * Assumes that the data at the start of the {@code buffer} is an IPv4 packet. In particular,
     * the version field (the first 4 bits) must equals 4.
     *
     * @param buffer the buffer
     * @return An IPv4 packet if available
     */
    public static IPv4Packet parse(ByteBuffer buffer) {
        int length = IPv4Header.readLength(buffer);
        assert length == -1 || IPv4Header.readVersion(buffer) == 4 : "This function must not be called when the packet is not IPv4";
        if (length == -1) {
            // no packet
            return null;
        }
        if (length > buffer.remaining()) {
            // no full packet available
            return null;
        }
        byte[] packetData = new byte[length];
        buffer.get(packetData);
        return new IPv4Packet(ByteBuffer.wrap(packetData));
    }
}
