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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * Convert from level 5 to level 3 by appending correct IP and transport headers.
 */
public class Packetizer {

    private final ByteBuffer buffer = ByteBuffer.allocate(IPv4Packet.MAX_PACKET_LENGTH);
    private final ByteBuffer payloadBuffer;

    private final IPv4Header responseIPv4Header;
    private final TransportHeader responseTransportHeader;

    public Packetizer(IPv4Header ipv4Header, TransportHeader transportHeader) {
        responseIPv4Header = ipv4Header.copyTo(buffer);
        responseTransportHeader = transportHeader.copyTo(buffer);
        payloadBuffer = buffer.slice();
    }

    public IPv4Header getResponseIPv4Header() {
        return responseIPv4Header;
    }

    public TransportHeader getResponseTransportHeader() {
        return responseTransportHeader;
    }

    public IPv4Packet packetize(ByteBuffer payload, int maxChunkSize) {
        payloadBuffer.limit(maxChunkSize).position(0);
        int payloadLength = payload.remaining();
        int chunkSize = Math.min(payloadLength, maxChunkSize);
        int savedLimit = payload.limit();
        payload.limit(payload.position() + chunkSize);
        payloadBuffer.put(payload);
        payloadBuffer.flip();
        payload.limit(savedLimit);
        return inflate();
    }

    public IPv4Packet packetize(ByteBuffer payload) {
        return packetize(payload, payloadBuffer.capacity());
    }

    public IPv4Packet packetize(ReadableByteChannel channel, int maxChunkSize) throws IOException {
        payloadBuffer.limit(maxChunkSize).position(0);
        int payloadLength = channel.read(payloadBuffer);
        if (payloadLength == -1) {
            return null;
        }
        payloadBuffer.flip();
        return inflate();
    }

    public IPv4Packet packetize(ReadableByteChannel channel) throws IOException {
        return packetize(channel, payloadBuffer.capacity());
    }

    private IPv4Packet inflate() {
        int payloadLength = payloadBuffer.remaining();
        buffer.limit(payloadBuffer.arrayOffset() + payloadBuffer.limit()).position(0);

        int ipv4HeaderLength = responseIPv4Header.getHeaderLength();
        int transportHeaderLength = responseTransportHeader.getHeaderLength();
        int totalLength = ipv4HeaderLength + transportHeaderLength + payloadLength;

        responseIPv4Header.setTotalLength(totalLength);
        responseTransportHeader.setPayloadLength(payloadLength);

        // In order to avoid copies, buffer is shared with this IPv4Packet instance that is returned.
        // Don't use it after another call to createPacket()!
        IPv4Packet packet = new IPv4Packet(buffer);
        packet.recompute();
        return packet;
    }
}
