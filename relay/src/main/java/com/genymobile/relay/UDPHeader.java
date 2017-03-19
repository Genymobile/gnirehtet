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

public class UDPHeader implements TransportHeader {

    private static final int UDP_HEADER_LENGTH = 8;

    private ByteBuffer raw;
    private int sourcePort;
    private int destinationPort;

    public UDPHeader(ByteBuffer raw) {
        this.raw = raw;
        raw.limit(UDP_HEADER_LENGTH);
        sourcePort = Short.toUnsignedInt(raw.getShort(0));
        destinationPort = Short.toUnsignedInt(raw.getShort(2));
    }

    @Override
    public int getSourcePort() {
        return sourcePort;
    }

    @Override
    public int getDestinationPort() {
        return destinationPort;
    }

    @Override
    public void setSourcePort(int sourcePort) {
        this.sourcePort = sourcePort;
        raw.putShort(0, (short) sourcePort);
    }

    @Override
    public void setDestinationPort(int destinationPort) {
        this.destinationPort = destinationPort;
        raw.putShort(2, (short) destinationPort);
    }

    @Override
    public int getHeaderLength() {
        return UDP_HEADER_LENGTH;
    }

    @Override
    public void setPayloadLength(int payloadLength) {
        int length = getHeaderLength() + payloadLength;
        raw.putShort(4, (short) length);
    }

    @Override
    public ByteBuffer getRaw() {
        raw.rewind();
        return raw.slice();
    }

    @Override
    public UDPHeader copyTo(ByteBuffer target) {
        raw.rewind();
        ByteBuffer slice = Binary.slice(target, target.position(), getHeaderLength());
        target.put(raw);
        return new UDPHeader(slice);
    }

    @Override
    public UDPHeader copy() {
        return new UDPHeader(Binary.copy(raw));
    }

    @Override
    public void computeChecksum(IPv4Header ipv4Header, ByteBuffer payload) {
        // disable checksum validation
        raw.putShort(6, (short) 0);
    }
}
