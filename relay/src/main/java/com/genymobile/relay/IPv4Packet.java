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


public class IPv4Packet {

    private static final String TAG = IPv4Packet.class.getSimpleName();

    @SuppressWarnings("checkstyle:MagicNumber")
    public static final int MAX_PACKET_LENGTH = 1 << 16; // packet length is stored on 16 bits

    private final ByteBuffer raw;
    private IPv4Header ipv4Header;
    private TransportHeader transportHeader;

    public IPv4Packet(ByteBuffer raw) {
        this.raw = raw;
        raw.rewind();

        ipv4Header = new IPv4Header(raw.duplicate());
        if (!ipv4Header.isSupported()) {
            Log.d(TAG, "Unsupported IPv4 headers");
            return;
        }
        transportHeader = createTransportHeader();
        raw.limit(ipv4Header.getTotalLength());
    }

    public static IPv4Packet merge(IPv4Header ipv4Header, TransportHeader transportHeader, ByteBuffer payload) {
        int ipv4HeaderLength = ipv4Header.getHeaderLength();
        int transportHeaderLength = transportHeader.getHeaderLength();
        int payloadLength = payload.limit();
        int totalLength = ipv4HeaderLength + transportHeaderLength + payloadLength;

        ipv4Header.setTotalLength(totalLength);
        transportHeader.setPayloadLength(payloadLength);

        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.put(ipv4Header.getRaw());
        buffer.put(transportHeader.getRaw());
        buffer.put(payload);
        buffer.flip();

        return new IPv4Packet(buffer);
    }

    public boolean isValid() {
        return transportHeader != null;
    }

    private TransportHeader createTransportHeader() {
        IPv4Header.Protocol protocol = ipv4Header.getProtocol();
        switch (protocol) {
            case UDP:
                return new UDPHeader(getRawTransport());
            case TCP:
                return new TCPHeader(getRawTransport());
            default:
                throw new AssertionError("Should be unreachable if ipv4Header.isSupported()");
        }
    }

    private ByteBuffer getRawTransport() {
        raw.position(ipv4Header.getHeaderLength());
        return raw.slice();
    }

    public IPv4Header getIpv4Header() {
        return ipv4Header;
    }

    public TransportHeader getTransportHeader() {
        return transportHeader;
    }

    public void switchSourceAndDestination() {
        ipv4Header.switchSourceAndDestination();
        transportHeader.switchSourceAndDestination();
    }

    public ByteBuffer getRaw() {
        raw.rewind();
        return raw.duplicate();
    }

    public int getRawLength() {
        return raw.limit();
    }

    public ByteBuffer getPayload() {
        int headersLength = ipv4Header.getHeaderLength() + transportHeader.getHeaderLength();
        raw.position(headersLength);
        return raw.slice();
    }

    public int getPayloadLength() {
        return raw.limit() - ipv4Header.getHeaderLength() - transportHeader.getHeaderLength();
    }

    public void recompute() {
        ipv4Header.computeChecksum();
        transportHeader.setPayloadLength(getPayloadLength());
        transportHeader.computeChecksum(ipv4Header, getPayload());
    }
}
