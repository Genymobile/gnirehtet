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

    private static final int IPV4_HEADER_MIN_LENGTH = 20;
    private static final int IPV4_VERSION_SHIFT_RELATED_TO_VERSION_AND_IHL = 4;
    private static final int MASK_4_LOWEST_BITS = 0xf;
    private static final int MASK_4_HIGHEST_BITS_IN_ONE_BYTE = 0xf0;
    private static final int MASK_16_LOWEST_BITS = 0xffff;
    private static final int MASK_ALL_EXCEPT_16_LOWEST_BITS = ~MASK_16_LOWEST_BITS;
    private static final int IPV4_PROTOCOL_NUMBER_OFFSET = 9;
    private static final int IPV4_SOURCE_OFFSET = 12;
    private static final int IPV4_DESTINATION_OFFSET = 16;
    private static final int IPV4_CHECKSUM_OFFSET = 10;
    private static final int IPV4_LENGTH_OFFSET = 2;
    private static final int IPV4_LENGTH_LENGTH = 2;
    private static final int IPV4_VERSION = 4;
    private static final int ONE_BYTE_IN_BITS = 8;

    private ByteBuffer raw;
    private byte version;
    private int headerLength;
    private int totalLength;
    private Protocol protocol;
    private int source;
    private int destination;

    public IPv4Header(ByteBuffer raw) {
        assert raw.limit() >= IPV4_HEADER_MIN_LENGTH : "IPv4 headers length must be at least 20 bytes";
        this.raw = raw;

        byte versionAndIHL = raw.get(0);
        version = (byte) (versionAndIHL >> IPV4_VERSION_SHIFT_RELATED_TO_VERSION_AND_IHL);

        byte ihl = (byte) (versionAndIHL & MASK_4_LOWEST_BITS);
        headerLength = ihl << 2;

        raw.limit(headerLength);

        totalLength = Short.toUnsignedInt(raw.getShort(2));
        //raw.limit(); // by design
        //assert totalLength == Binary.unsigned(raw.getShort(2)) : "Inconsistent packet length";

        int protocolNumber = Short.toUnsignedInt(raw.get(IPV4_PROTOCOL_NUMBER_OFFSET));
        protocol = Protocol.fromNumber(protocolNumber);

        source = raw.getInt(IPV4_SOURCE_OFFSET);
        destination = raw.getInt(IPV4_DESTINATION_OFFSET);
    }

    public boolean isSupported() {
        return version == IPV4_VERSION && protocol != Protocol.OTHER;
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
        raw.putInt(IPV4_SOURCE_OFFSET, source);
    }

    public void setDestination(int destination) {
        this.destination = destination;
        raw.putInt(IPV4_DESTINATION_OFFSET, destination);
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
        while ((sum & MASK_ALL_EXCEPT_16_LOWEST_BITS) != 0) {
            sum = (sum & MASK_16_LOWEST_BITS) + (sum >> (2 * ONE_BYTE_IN_BITS));
        }
        setChecksum((short) ~sum);
    }

    private void setChecksum(short checksum) {
        raw.putShort(IPV4_CHECKSUM_OFFSET, checksum);
    }

    public short getChecksum() {
        return raw.getShort(IPV4_CHECKSUM_OFFSET);
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
        return (versionAndIHL & MASK_4_HIGHEST_BITS_IN_ONE_BYTE) >> IPV4_VERSION_SHIFT_RELATED_TO_VERSION_AND_IHL;
    }

    /**
     * Read the packet length, assuming thatan IP packet is stored at absolute position 0.
     *
     * @param buffer the buffer
     * @return the packet length, or {@code -1} if not available
     */
    public static int readLength(ByteBuffer buffer) {
        if (buffer.limit() < IPV4_LENGTH_OFFSET + IPV4_LENGTH_LENGTH) {
            // buffer does not even contains the length field
            return -1;
        }
        // packet length is 16 bits starting at offset 2
        return Short.toUnsignedInt(buffer.getShort(2));
    }
}
