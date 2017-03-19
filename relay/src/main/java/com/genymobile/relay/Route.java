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
import java.net.InetSocketAddress;
import java.nio.channels.Selector;

public class Route {

    private final Client client;
    private final Key key;
    private final Connection connection;
    private final RemoveHandler<Route.Key> removeHandler;

    public Route(Client client, Selector selector, Key key, IPv4Header ipv4Header, TransportHeader transportHeader, RemoveHandler<Route.Key> removeHandler) throws IOException {
        this.client = client;
        this.key = key;
        connection = createConnection(selector, key, ipv4Header, transportHeader);
        this.removeHandler = removeHandler;
    }

    private Connection createConnection(Selector selector, Key key, IPv4Header ipv4Header, TransportHeader transportHeader) throws IOException {
        IPv4Header.Protocol protocol = key.getProtocol();
        if (protocol == IPv4Header.Protocol.UDP) {
            return new UDPConnection(this, selector, ipv4Header, (UDPHeader) transportHeader);
        }
        if (protocol == IPv4Header.Protocol.TCP) {
            return new TCPConnection(this, selector, ipv4Header, (TCPHeader) transportHeader);
        }
        throw new UnsupportedOperationException("Unsupported protocol: " + protocol);
    }

    public boolean isConnectionExpired() {
        return connection.isExpired();
    }

    public void discard() {
        removeHandler.remove(key);
    }

    public void disconnect() {
        connection.disconnect();
    }

    public Key getKey() {
        return key;
    }

    public void sendToNetwork(IPv4Packet packet) {
        connection.sendToNetwork(packet);
    }

    public boolean sendToClient(IPv4Packet packet) {
        return client.sendToClient(packet);
    }

    public void consume(PacketSource source) {
        client.consume(source);
    }

    public static class Key {
        private IPv4Header.Protocol protocol;
        private int sourceIp;
        private short sourcePort;
        private int destIp;
        private short destPort;

        public Key(IPv4Header.Protocol protocol, int sourceIp, short sourcePort, int destIp, short destPort) {
            this.protocol = protocol;
            this.sourceIp = sourceIp;
            this.sourcePort = sourcePort;
            this.destIp = destIp;
            this.destPort = destPort;
        }

        public IPv4Header.Protocol getProtocol() {
            return protocol;
        }

        private InetSocketAddress getSource() {
            return new InetSocketAddress(Net.toInetAddress(sourceIp), Short.toUnsignedInt(sourcePort));
        }

        private InetSocketAddress getDestination() {
            return new InetSocketAddress(Net.toInetAddress(destIp), Short.toUnsignedInt(destPort));
        }

        public int getSourceIp() {
            return sourceIp;
        }

        public int getSourcePort() {
            return Short.toUnsignedInt(sourcePort);
        }

        public int getDestinationIp() {
            return destIp;
        }

        public int getDestinationPort() {
            return Short.toUnsignedInt(destPort);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Key key = (Key) o;

            if (sourceIp != key.sourceIp) return false;
            if (sourcePort != key.sourcePort) return false;
            if (destIp != key.destIp) return false;
            if (destPort != key.destPort) return false;
            return protocol == key.protocol;
        }

        @Override
        public int hashCode() {
            int result = protocol.hashCode();
            result = 31 * result + sourceIp;
            result = 31 * result + (int) sourcePort;
            result = 31 * result + destIp;
            result = 31 * result + (int) destPort;
            return result;
        }

        @Override
        public String toString() {
            return protocol + " " + Net.toString(getSource()) + " -> " + Net.toString(getDestination());
        }
    }

    public static Key getKey(IPv4Header ipv4Header, TransportHeader transportHeader) {
        IPv4Header.Protocol protocol = ipv4Header.getProtocol();
        int sourceAddress = ipv4Header.getSource();
        short sourcePort = (short) transportHeader.getSourcePort();
        int destinationAddress = ipv4Header.getDestination();
        short destinationPort = (short) transportHeader.getDestinationPort();
        return new Key(protocol, sourceAddress, sourcePort, destinationAddress, destinationPort);
    }
}
