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

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Random;

public class TCPConnection extends AbstractConnection implements PacketSource {

    private static final String TAG = TCPConnection.class.getSimpleName();

    // same value as GnirehtetService.MTU in the client
    private static final int MTU = 0x4000;
    // 20 bytes for IP headers, 20 bytes for TCP headers
    private static final int MAX_PAYLOAD_SIZE = MTU - 20 - 20;

    private static final Random RANDOM = new Random();

    public enum State {
        SYN_SENT,
        SYN_RECEIVED,
        ESTABLISHED,
        LAST_ACK
    }

    private final StreamBuffer clientToNetwork = new StreamBuffer(4 * IPv4Packet.MAX_PACKET_LENGTH);
    private final Packetizer networkToClient;
    private IPv4Packet packetForClient;

    private final SocketChannel channel;
    private final SelectionKey selectionKey;
    private int interests;

    private State state;
    private int synSequenceNumber;
    private int sequenceNumber;
    private int acknowledgementNumber;

    private int theirAcknowledgementNumber;
    private int clientWindow;

    private boolean remoteClosed;

    public TCPConnection(ConnectionId id, Client client, Selector selector, IPv4Header ipv4Header, TCPHeader tcpHeader) throws IOException {
        super(id, client);

        TCPHeader shrinkedTcpHeader = tcpHeader.copy();
        shrinkedTcpHeader.shrinkOptions(); // no TCP options

        networkToClient = new Packetizer(ipv4Header, shrinkedTcpHeader);
        networkToClient.getResponseIPv4Header().swapSourceAndDestination();
        networkToClient.getResponseTransportHeader().swapSourceAndDestination();

        SelectionHandler selectionHandler = (selectionKey) -> {
            if (selectionKey.isValid() && selectionKey.isConnectable()) {
                processConnect();
            }
            if (selectionKey.isValid() && selectionKey.isReadable()) {
                processReceive();
            }
            if (selectionKey.isValid() && selectionKey.isWritable()) {
                processSend();
            }
            updateInterests();
        };
        channel = createChannel();
        // interests will be set on the first packet received
        // set the initial value now so that they won't need to be updated
        interests = SelectionKey.OP_CONNECT;
        selectionKey = channel.register(selector, interests, selectionHandler);
    }

    @Override
    public void disconnect() {
        logi(TAG, "Close");
        selectionKey.cancel();
        try {
            channel.close();
        } catch (IOException e) {
            loge(TAG, "Cannot close connection channel", e);
        }
    }

    private void processReceive() {
        try {
            assert packetForClient == null : "The IPv4Packet shares the networkToClient buffer, it must not be corrupted";
            int remainingClientWindow = getRemainingClientWindow();
            assert remainingClientWindow > 0 : "If remainingClientWindow is 0, then processReceive() should not have been called";
            int maxPayloadSize = Math.min(remainingClientWindow, MAX_PAYLOAD_SIZE);
            updateHeaders(TCPHeader.FLAG_ACK | TCPHeader.FLAG_PSH);
            packetForClient = networkToClient.packetize(channel, maxPayloadSize);
            if (packetForClient == null) {
                eof();
                return;
            }
            consume(this);
        } catch (IOException e) {
            loge(TAG, "Cannot read", e);
            resetConnection();
        }
    }

    private void processSend() {
        try {
            if (clientToNetwork.writeTo(channel) == -1) {
                close();
            }
        } catch (IOException e) {
            loge(TAG, "Cannot write", e);
            resetConnection();
        }
    }

    private void eof() {
        remoteClosed = true;
    }

    private int getRemainingClientWindow() {
        // in Java, (signed) integer overflow is well-defined: it wraps around
        int remaining = theirAcknowledgementNumber + clientWindow - sequenceNumber;
        if (remaining < 0 || remaining > clientWindow) {
            // our sequence number is outside their window
            return 0;
        }
        return remaining;
    }

    @Override
    public boolean isExpired() {
        // no external timeout expiration
        return false;
    }

    private void updateHeaders(int flags) {
        TCPHeader tcpHeader = (TCPHeader) networkToClient.getResponseTransportHeader();
        tcpHeader.setFlags(flags);
        tcpHeader.setSequenceNumber(sequenceNumber);
        tcpHeader.setAcknowledgementNumber(acknowledgementNumber);
    }

    private SocketChannel createChannel() throws IOException {
        logi(TAG, "Open");
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.connect(getRewrittenDestination());
        return socketChannel;
    }

    @Override
    public void sendToNetwork(IPv4Packet packet) {
        handlePacket(packet);
        logd(TAG, "current ack=" + acknowledgementNumber);
        updateInterests();
    }

    private void handlePacket(IPv4Packet packet) {
        TCPHeader tcpHeader = (TCPHeader) packet.getTransportHeader();
        if (state == null) {
            handleFirstPacket(packet);
            return;
        }

        if (tcpHeader.isSyn()) {
            // the client always initiates the connection
            // at this point, any SYN packet received is duplicate
            handleDuplicateSyn(packet);
            return;
        }

        int packetSequenceNumber = tcpHeader.getSequenceNumber();
        if (packetSequenceNumber != acknowledgementNumber) {
            // ignore packet already received or out-of-order, retransmission is already managed by both sides
            logw(TAG, "Ignoring packet " + packetSequenceNumber + "; expecting " + acknowledgementNumber + "; flags=" + tcpHeader.getFlags());
            sendToClient(createEmptyResponsePacket(TCPHeader.FLAG_ACK)); // re-ack
            return;
        }

        clientWindow = tcpHeader.getWindow();
        theirAcknowledgementNumber = tcpHeader.getAcknowledgementNumber();

        logd(TAG, "Receiving expected paquet " + packetSequenceNumber + " (flags = " + tcpHeader.getFlags() + ")");

        if (tcpHeader.isRst()) {
            logd(TAG, "Reset requested, closing");
            close();
            return;
        }

        if (tcpHeader.isAck()) {
            logd(TAG, "Client acked " + tcpHeader.getAcknowledgementNumber());
        }

        if (tcpHeader.isFin()) {
            handleFin(packet);
            return;
        }

        if (tcpHeader.isAck()) {
            handleAck(packet);
        }
    }

    private void handleFirstPacket(IPv4Packet packet) {
        logd(TAG, "handleFirstPacket()");
        TCPHeader tcpHeader = (TCPHeader) packet.getTransportHeader();
        if (!tcpHeader.isSyn()) {
            logw(TAG, "Unexpected first packet " + tcpHeader.getSequenceNumber() + "; acking " + tcpHeader.getAcknowledgementNumber()
                    + "; flags=" + tcpHeader.getFlags());
            sequenceNumber = tcpHeader.getAcknowledgementNumber(); // make a RST in the window client
            resetConnection();
            return;
        }

        int theirSequenceNumber = tcpHeader.getSequenceNumber();
        acknowledgementNumber = theirSequenceNumber + 1;
        synSequenceNumber = theirSequenceNumber;

        sequenceNumber = RANDOM.nextInt();
        logd(TAG, "initialized seqNum=" + sequenceNumber + "; ackNum=" + acknowledgementNumber);
        clientWindow = tcpHeader.getWindow();
        state = State.SYN_SENT;
    }

    private void handleDuplicateSyn(IPv4Packet packet) {
        TCPHeader tcpHeader = (TCPHeader) packet.getTransportHeader();
        int theirSequenceNumber = tcpHeader.getSequenceNumber();
        if (state == State.SYN_SENT) {
            // we didn't call finishConnect() yet, we can accept this packet as if it were the first SYN
            synSequenceNumber = theirSequenceNumber;
            acknowledgementNumber = theirSequenceNumber + 1;
        } else if (theirSequenceNumber != synSequenceNumber) {
            // duplicate SYN with different sequence number
            resetConnection();
        }
    }

    private void handleFin(IPv4Packet packet) {
        TCPHeader tcpHeader = (TCPHeader) packet.getTransportHeader();
        acknowledgementNumber = tcpHeader.getSequenceNumber() + 1;
        // consider the connection closed immediately (no need for CLOSE_WAIT)
        state = State.LAST_ACK;
        remoteClosed = true;
        logd(TAG, "Received a FIN from the client, sending ACK+FIN " + numbers());
        IPv4Packet response = createEmptyResponsePacket(TCPHeader.FLAG_FIN | TCPHeader.FLAG_ACK);
        ++sequenceNumber; // FIN counts for 1 byte
        sendToClient(response);
    }

    private void handleAck(IPv4Packet packet) {
        logd(TAG, "handleAck()");
        if (state == State.SYN_RECEIVED) {
            logd(TAG, "ESTABLISHED");
            state = State.ESTABLISHED;
            return;
        }
        if (state == State.LAST_ACK) {
            logd(TAG, "LAST_ACK");
            close();
            return;
        }

        if (Log.isVerboseEnabled()) {
            logv(TAG, Binary.toString(packet.getRaw()));
        }

        int payloadLength = packet.getPayloadLength();
        if (payloadLength == 0) {
            // no data to transmit
            return;
        }

        if (clientToNetwork.remaining() < payloadLength) {
            logw(TAG, "Not enough space, dropping packet");
            return;
        }

        clientToNetwork.readFrom(packet.getPayload());
        acknowledgementNumber += payloadLength;

        // send ACK to client
        logd(TAG, "Received a payload from the client (" + payloadLength + " bytes), sending ACK " + numbers());
        IPv4Packet responsePacket = createEmptyResponsePacket(TCPHeader.FLAG_ACK);
        sendToClient(responsePacket);
    }

    private void processConnect() {
        logd(TAG, "processConnect()");
        if (!finishConnect()) {
            close();
            return;
        }
        logd(TAG, "SYN_RECEIVED, acking " + numbers());
        state = State.SYN_RECEIVED;
        IPv4Packet packet = createEmptyResponsePacket(TCPHeader.FLAG_SYN | TCPHeader.FLAG_ACK);
        ++sequenceNumber; // SYN counts for 1 byte
        sendToClient(packet);
    }

    private boolean finishConnect() {
        try {
            return channel.finishConnect();
        } catch (IOException e) {
            loge(TAG, "Cannot finish connect", e);
            return false;
        }
    }

    private void resetConnection() {
        logd(TAG, "Resetting connection");
        state = null;
        IPv4Packet packet = createEmptyResponsePacket(TCPHeader.FLAG_RST);
        sendToClient(packet);
        close();
    }

    private IPv4Packet createEmptyResponsePacket(int flags) {
        updateHeaders(flags);
        IPv4Packet packet = networkToClient.packetizeEmptyPayload();
        logd(TAG, "Forging empty response (flags=" + flags + ") " + numbers());
        if (Log.isVerboseEnabled()) {
            logd(TAG, Binary.toString(packet.getRaw()));
        }
        if ((flags & TCPHeader.FLAG_ACK) != 0) {
            logd(TAG, "Acking " + numbers());
        }
        return packet;
    }

    protected void updateInterests() {
        if (!selectionKey.isValid()) {
            return;
        }
        int interestOps = 0;
        if (mayRead()) {
            interestOps |= SelectionKey.OP_READ;
        }
        if (mayWrite()) {
            interestOps |= SelectionKey.OP_WRITE;
        }
        if (mayConnect()) {
            interestOps |= SelectionKey.OP_CONNECT;
        }
        if (interests != interestOps) {
            // interests must be changed
            interests = interestOps;
            selectionKey.interestOps(interestOps);
        }
    }

    private boolean mayRead() {
        if (remoteClosed) {
            return false;
        }
        if (state == State.SYN_SENT || state == State.SYN_RECEIVED) {
            // not connected yet
            return false;
        }
        if (packetForClient != null) {
            // a packet is already pending
            return false;
        }
        return getRemainingClientWindow() > 0;
    }

    private boolean mayWrite() {
        return !clientToNetwork.isEmpty();
    }

    private boolean mayConnect() {
        return state == State.SYN_SENT;
    }

    private String numbers() {
        return "(seq=" + sequenceNumber + ", ack=" + acknowledgementNumber + ")";
    }

    @Override
    public IPv4Packet get() {
        // TODO update only when necessary
        updateAcknowledgementNumber(packetForClient);
        return packetForClient;
    }

    private void updateAcknowledgementNumber(IPv4Packet packet) {
        TCPHeader tcpHeader = (TCPHeader) packet.getTransportHeader();
        tcpHeader.setAcknowledgementNumber(acknowledgementNumber);
        packet.computeChecksums();
    }

    @Override
    public void next() {
        logd(TAG, "Packet (" + packetForClient.getPayloadLength() + " bytes) sent to client " + numbers());
        if (Log.isVerboseEnabled()) {
            logv(TAG, Binary.toString(packetForClient.getRaw()));
        }
        sequenceNumber += packetForClient.getPayloadLength();
        packetForClient = null;
        updateInterests();
    }
}
