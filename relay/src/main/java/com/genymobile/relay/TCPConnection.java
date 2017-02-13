package com.genymobile.relay;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Random;

import static java.nio.channels.SelectionKey.OP_READ;

public class TCPConnection extends AbstractConnection {

    private static final String TAG = TCPConnection.class.getName();

    private static final ByteBuffer ZERO_LENGTH_BUFFER = ByteBuffer.allocate(0);
    private static final Random RANDOM = new Random();

    public enum State {
        SYN_SENT,
        SYN_RECEIVED,
        ESTABLISHED,
        CLOSE_WAIT,
        LAST_ACK
    }

    private final StreamBuffer clientToNetwork = new StreamBuffer(4 * IPv4Packet.MAX_PACKET_LENGTH);
    private final NetBuffer networkToClient = new NetBuffer(1);

    private final SocketChannel channel;
    private final SelectionKey selectionKey;

    private final IPv4Header responseIPv4Header;
    private final TCPHeader responseTCPHeader;

    private State state;
    private int sequenceNumber;
    private int acknowledgementNumber;

    private boolean remoteClosed;

    public TCPConnection(Route route, Selector selector, IPv4Header ipv4Header, TCPHeader tcpHeader) throws IOException {
        super(route);

        responseIPv4Header = ipv4Header.copy();
        responseIPv4Header.switchSourceAndDestination();

        tcpHeader.shrinkOptions(); // no options
        responseTCPHeader = tcpHeader.copy();
        responseTCPHeader.switchSourceAndDestination();

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
            if (selectionKey.isValid()) {
                updateInterests();
            }
        };
        channel = createChannel();
        selectionKey = channel.register(selector, OP_READ | SelectionKey.OP_CONNECT, selectionHandler);
    }

    @Override
    public void disconnect() {
        selectionKey.cancel();
        try {
            channel.close();
        } catch (IOException e) {
            Log.e(TAG, "Cannot close connection channel", e);
        }
    }

    private void processReceive() {
        try {
            if (!networkToClient.read(channel)) {
                eof();
                return;
            }
            pushToClient();
        } catch (IOException e) {
            Log.e(TAG, "Cannot read", e);
            resetConnection();
            destroy();
        }
    }

    private void processSend() {
        try {
            if (clientToNetwork.writeTo(channel) == -1) {
                destroy();
                return;
            }
        } catch (IOException e) {
            Log.e(TAG, "Cannot write", e);
            resetConnection();
            destroy();
        }
    }

    private void eof() {
        remoteClosed = true;
        if (state == State.CLOSE_WAIT) {
            IPv4Packet packet = createEmptyResponsePacket(TCPHeader.FLAG_FIN, sequenceNumber, acknowledgementNumber);
            ++sequenceNumber; // FIN count as 1 byte
            sendToClient(packet);
        }
    }

    private void pushToClient() {
        ByteBuffer buffer;
        while ((buffer = networkToClient.poll()) != null) {
            IPv4Packet packet = IPv4Packet.merge(responseIPv4Header, responseTCPHeader, buffer);
            updateHeaders(packet);
            packet.recompute();
            Log.d(TAG, route.getKey() + " PACKET SEND TO CLIENT " + packet.getPayloadLength() + Binary.toString(packet.getRaw()));
            sendToClient(packet);
            sequenceNumber += packet.getPayloadLength();
        }
    }

    @Override
    public boolean isExpired() {
        // no external timeout expiration
        return false;
    }

    private void updateHeaders(IPv4Packet packet) {
        TCPHeader tcpHeader = (TCPHeader) packet.getTransportHeader();
        tcpHeader.setFlags(TCPHeader.FLAG_ACK | TCPHeader.FLAG_PSH);
        tcpHeader.setSequenceNumber(sequenceNumber);
        tcpHeader.setAcknowledgementNumber(acknowledgementNumber);
    }

    private SocketChannel createChannel() throws IOException {
        Route.Key key = route.getKey();
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.connect(key.getDestination());
        Log.d(TAG, "TCP dest = " + key.getDestination() + " (from " + key.getSourcePort() + ")");
        return channel;
    }

    @Override
    public void sendToNetwork(IPv4Packet packet) {
        handlePacket(packet);
        Log.d(TAG, route.getKey() + " new ackNum = " + acknowledgementNumber);
        if (selectionKey.isValid()) {
            updateInterests();
        }
    }

    private void handlePacket(IPv4Packet packet) {
        TCPHeader tcpHeader = (TCPHeader) packet.getTransportHeader();
        if (tcpHeader.isRst()) {
            Log.d(TAG, route.getKey() + " Reset requested, closing");
            destroy();
            return;
        }

        if (state == null) {
            handleFirstPacket(packet);
            return;
        }

        // TODO incorrect if receive packet out-of-order from the client
        int packetSequenceNumber = tcpHeader.getSequenceNumber();
        // expect packets in order
        if (packetSequenceNumber != acknowledgementNumber) {
            // ignore packet already received or out-of-order, retransmission is already managed by both sides
            Log.d(TAG, route.getKey() + " Ignoring packet " + packetSequenceNumber + "; expecting " + acknowledgementNumber + "; isSyn()=" + tcpHeader.isSyn());
            return;
        }

        Log.d(TAG, route.getKey() + " receiving expected paquet " + packetSequenceNumber + " (flags = " + tcpHeader.getFlags() + ")");
//        acknowledgementNumber = packetSequenceNumber + packet.getPayloadLength();

        if (tcpHeader.isSyn()) {
            // the client always initiates the connection
            // at this point, any SYN packet received is duplicate
            handleDuplicateSyn(packet);
            return;
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
        Log.d(TAG, "handleFirstPacket");
        TCPHeader tcpHeader = (TCPHeader) packet.getTransportHeader();
        if (!tcpHeader.isSyn()) {
            resetConnection();
            return;
        }

        sequenceNumber = RANDOM.nextInt();
        acknowledgementNumber = tcpHeader.getSequenceNumber() + 1;
        Log.d(TAG, route.getKey() + " initialized seqNum=" + sequenceNumber + "; ackNum=" + acknowledgementNumber);
        state = State.SYN_SENT;
    }

    private void handleDuplicateSyn(IPv4Packet packet) {
        if (state == State.SYN_SENT) {
            TCPHeader tcpHeader = (TCPHeader) packet.getTransportHeader();
            // we didn't call finishConnect() yet, we can accept this packet as if it were the first SYN
            acknowledgementNumber = tcpHeader.getSequenceNumber() + 1;
        } else {
            resetConnection();
        }
    }

    private void handleFin(IPv4Packet packet) {
        TCPHeader tcpHeader = (TCPHeader) packet.getTransportHeader();
        acknowledgementNumber = tcpHeader.getSequenceNumber() + 1;
        if (remoteClosed) {
            state = State.LAST_ACK;
            IPv4Packet response = createEmptyResponsePacket(TCPHeader.FLAG_FIN | TCPHeader.FLAG_ACK, sequenceNumber, acknowledgementNumber);
            ++sequenceNumber; // FIN count as 1 byte
            sendToClient(response);
        } else {
            state = State.CLOSE_WAIT;
            IPv4Packet response = createEmptyResponsePacket(TCPHeader.FLAG_ACK, sequenceNumber, acknowledgementNumber);
            sendToClient(response);
        }
    }

    private void handleAck(IPv4Packet packet) {
        TCPHeader tcpHeader = (TCPHeader) packet.getTransportHeader();
        Log.d(TAG, route.getKey() + " handleAck");
        if (state == State.SYN_RECEIVED) {
            Log.d(TAG, route.getKey() + " ESTABLISHED");
            state = State.ESTABLISHED;
            return;
        }
        if (state == State.LAST_ACK) {
            Log.d(TAG, route.getKey() + " LAST_ACK --> destroy");
            destroy();
            return;
        }

        System.out.println(Binary.toString(packet.getRaw()));

        int payloadLength = packet.getPayloadLength();
        if (payloadLength == 0) {
            // no data to transmit
            return;
        }

        if (clientToNetwork.remaining() < payloadLength) {
            Log.d(TAG, "Not enough space, drop packet");
            return;
        }

        clientToNetwork.readFrom(packet.getPayload());

        // send ACK to client
        Log.d(TAG, route.getKey() + " Received a payload from the client (" + payloadLength + "), sending ACK");
        acknowledgementNumber += payloadLength;
        IPv4Packet responsePacket = createEmptyResponsePacket(TCPHeader.FLAG_ACK, sequenceNumber, acknowledgementNumber);
        sendToClient(responsePacket);
    }

    private void processConnect() {
        Log.d(TAG, route.getKey() + " processConnect");
        if (!finishConnect()) {
            destroy();
            return;
        }
        Log.d(TAG, route.getKey() + " SYN_RECEIVED acking " + acknowledgementNumber);
        state = State.SYN_RECEIVED;
        IPv4Packet packet = createEmptyResponsePacket(TCPHeader.FLAG_SYN | TCPHeader.FLAG_ACK, sequenceNumber, acknowledgementNumber);
        ++sequenceNumber; // SYN count as 1 byte
        sendToClient(packet);
    }

    private boolean finishConnect() {
        try {
            return channel.finishConnect();
        } catch (IOException e) {
            Log.e(TAG, "Cannot finish connect", e);
            return false;
        }
    }

    private void resetConnection() {
        Log.d(TAG, route.getKey() + " I'm resetting connection " + route.getKey().getDestination());
        state = null;
        IPv4Packet packet = createEmptyResponsePacket(TCPHeader.FLAG_RST, sequenceNumber, acknowledgementNumber);
        route.sendToClient(packet);
    }

    private IPv4Packet createEmptyResponsePacket(int flags, int seqNum, int ackNum) {
        IPv4Packet packet = IPv4Packet.merge(responseIPv4Header, responseTCPHeader, ZERO_LENGTH_BUFFER);
        TCPHeader tcpHeader = (TCPHeader) packet.getTransportHeader();
        tcpHeader.setFlags(flags);
        tcpHeader.setSequenceNumber(seqNum);
        tcpHeader.setAcknowledgementNumber(ackNum);
        // TODO avoid double-recompute due to newResponsePacket(…)
        packet.recompute();
        Log.d(TAG, route.getKey() + " Forging empty response:" + Binary.toString(packet.getRaw()));
        return packet;
    }

    protected void updateInterests() {
        int interestingOps = 0;
        if (!remoteClosed) {
            interestingOps |= SelectionKey.OP_READ;
        }
        if (hasPendingWrites()) {
            interestingOps |= SelectionKey.OP_WRITE;
        }
        if (state == State.SYN_SENT) {
            interestingOps |= SelectionKey.OP_CONNECT;
        }
        selectionKey.interestOps(interestingOps);
    }

    private boolean hasPendingWrites() {
        return !clientToNetwork.isEmpty();
    }
}
