package com.genymobile.relay;

import java.nio.ByteBuffer;

public class IPv4Header {

    public enum Protocol {
        TCP(6), UDP(17), OTHER(-1);

        private final int number;

        Protocol(int number) {
            this.number = number;
        }

        int getNumber() {
            return number;
        }

        static Protocol fromNumber(int number) {
            if (number == TCP.number) {
                return TCP;
            }
            if (number == UDP.number) {
                return UDP;
            }
            return OTHER;
        }
    }

    private ByteBuffer raw;
    private byte version;
    private int headerLength;
    private int totalLength;
    private Protocol protocol;
    private int source;
    private int destination;

    public IPv4Header(ByteBuffer raw) {
        assert raw.limit() >= 20 : "IPv4 headers length must be at least 20 bytes";
        this.raw = raw;

        byte versionAndIHL = raw.get(0);
        version = (byte) (versionAndIHL >> 4);

        byte IHL = (byte) (versionAndIHL & 0xf);
        headerLength = IHL << 2;

        raw.limit(headerLength);

        totalLength = Short.toUnsignedInt(raw.getShort(2));
        //raw.limit(); // by design
        //assert totalLength == Binary.unsigned(raw.getShort(2)) : "Inconsistent packet length";

        int protocolNumber = Short.toUnsignedInt(raw.get(9));
        protocol = Protocol.fromNumber(protocolNumber);

        source = raw.getInt(12);
        destination = raw.getInt(16);
    }

    public boolean isSupported() {
        return version == 4 && protocol != Protocol.OTHER;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public int getHeaderLength() {
        return headerLength;
    }

    public int getTotalLength() {
        return totalLength;
    }

    public void setTotalLength(int totalLength) {
        this.totalLength = totalLength;
        // apply changes to raw
        raw.putShort(2, (short) totalLength);
    }

    public int getSource() {
        return source;
    }

    public int getDestination() {
        return destination;
    }

    public void setSource(int source) {
        this.source = source;
        raw.putInt(12, source);
    }

    public void setDestination(int destination) {
        this.destination = destination;
        raw.putInt(16, destination);
    }

    public void switchSourceAndDestination() {
        int tmp = source;
        setSource(destination);
        setDestination(tmp);
    }

    public ByteBuffer getRaw() {
        raw.rewind();
        return raw.slice();
    }

    public IPv4Header copyTo(ByteBuffer target) {
        raw.rewind();
        ByteBuffer slice = Binary.slice(target, target.position(), getHeaderLength());
        target.put(raw);
        return new IPv4Header(slice);
    }

    public IPv4Header copy() {
        return new IPv4Header(Binary.copy(raw));
    }

    public void computeChecksum() {
        // reset checksum field
        setChecksum((short) 0);

        int sum = 0;
        raw.rewind();
        while (raw.hasRemaining()) {
            sum += Short.toUnsignedInt(raw.getShort());
        }
        while ((sum & ~0xffff) != 0) {
            sum = (sum & 0xffff) + (sum >> 16);
        }
        setChecksum((short) ~sum);
    }

    private void setChecksum(short checksum) {
        raw.putShort(10, checksum);
    }

    public short getChecksum() {
        return raw.getShort(10);
    }

    /**
     * Read the packet IP version, assuming that an IP packets is stored at absolute position 0.
     *
     * @param buffer the buffer
     * @return the IP version, or {@code -1} if not available
     */
    public static int readVersion(ByteBuffer buffer) {
        if (buffer.limit() == 0) {
            // buffer is empty
            return -1;
        }
        // version is stored in the 4 first bits
        byte versionAndIHL = buffer.get(0);
        return (versionAndIHL & 0xf0) >> 4;
    }

    /**
     * Read the packet length, assuming thatan IP packet is stored at absolute position 0.
     *
     * @param buffer the buffer
     * @return the packet length, or {@code -1} if not available
     */
    public static int readLength(ByteBuffer buffer) {
        if (buffer.limit() < 4) {
            // buffer does not even contains the length field
            return -1;
        }
        // packet length is 16 bits starting at offset 2
        return Short.toUnsignedInt(buffer.getShort(2));
    }
}
