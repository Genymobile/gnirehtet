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

import java.nio.ByteBuffer;

@SuppressWarnings("checkstyle:MagicNumber")
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

    private static final int MIN_IPV4_HEADER_LENGTH = 20;

    private ByteBuffer raw;
    private byte version;
    private int headerLength;
    private int totalLength;
    private Protocol protocol;
    private int source;
    private int destination;

    public IPv4Header(ByteBuffer raw) {
        assert raw.limit() >= MIN_IPV4_HEADER_LENGTH : "IPv4 headers length must be at least 20 bytes";
        this.raw = raw;

        byte versionAndIHL = raw.get(0);
        version = (byte) (versionAndIHL >> 4);

        byte ihl = (byte) (versionAndIHL & 0xf);
        headerLength = ihl << 2;

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

    public void swapSourceAndDestination() {
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

        // checksum computation is the most CPU-intensive task in gnirehtet
        // prefer optimization over readability
        byte[] rawArray = raw.array();
        int rawArrayOffset = raw.arrayOffset();

        int sum = 0;
        for (int i = 0; i < headerLength / 2; ++i) {
            // compute a 16-bit value from two 8-bit values manually
            sum += (rawArray[rawArrayOffset + 2 * i] & 0xff) << 8 | (rawArray[rawArrayOffset + 2 * i + 1] & 0xff);
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
