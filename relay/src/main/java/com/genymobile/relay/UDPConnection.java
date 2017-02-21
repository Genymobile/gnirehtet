package com.genymobile.relay;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

public class UDPConnection extends AbstractConnection {

    public static final long IDLE_TIMEOUT = 2 * 60 * 1000;

    private static final String TAG = UDPConnection.class.getName();

    private final DatagramBuffer clientToNetwork = new DatagramBuffer(4 * IPv4Packet.MAX_PACKET_LENGTH);
    private final ByteBuffer networkToClient = ByteBuffer.allocate(IPv4Packet.MAX_PACKET_LENGTH);
    private boolean pendingDatagramForClient;

    private final IPv4Header responseIPv4Header;
    private final UDPHeader responseUDPHeader;

    private final DatagramChannel channel;
    private final SelectionKey selectionKey;

    private long idleSince;

    public UDPConnection(Route route, Selector selector, IPv4Header ipv4Header, UDPHeader udpHeader) throws IOException {
        super(route);

        responseIPv4Header = ipv4Header.copy();
        responseIPv4Header.switchSourceAndDestination();

        responseUDPHeader = udpHeader.copy();
        responseUDPHeader.switchSourceAndDestination();

        touch();

        SelectionHandler selectionHandler = (selectionKey) -> {
            touch();
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
        selectionKey = channel.register(selector, SelectionKey.OP_READ, selectionHandler);
    }

    @Override
    public void sendToNetwork(IPv4Packet packet) {
        if (!clientToNetwork.readFrom(packet.getPayload())) {
            Log.d(TAG, "Cannot processSend to network, drop packet");
            return;
        }
        updateInterests();
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

    @Override
    public boolean isExpired() {
        if (hasPendingWrites()) {
            return false;
        }
        return System.currentTimeMillis() >= idleSince + IDLE_TIMEOUT;
    }

    private DatagramChannel createChannel() throws IOException {
        Route.Key key = route.getKey();
        DatagramChannel channel = DatagramChannel.open();
        channel.configureBlocking(false);
        channel.connect(key.getDestination());
        Log.d(TAG, "UDP dest = " + key.getDestination());
        return channel;
    }

    private void touch() {
        idleSince = System.currentTimeMillis();
    }

    private boolean hasPendingWrites() {
        return !clientToNetwork.isEmpty();
    }

    private void processReceive() {
        assert !pendingDatagramForClient;
        if (!read()) {
            destroy();
            return;
        }
        pushToClient();
    }

    private void processSend() {
        if (!write()) {
            destroy();
            return;
        }
    }

    private boolean read() {
        try {
            boolean ok = channel.read(networkToClient) != -1;
            networkToClient.flip();
            pendingDatagramForClient = true;
            return ok;
        } catch (IOException e) {
            Log.e(TAG, "Cannot read", e);
            return false;
        }
    }

    private boolean write() {
        try {
            return clientToNetwork.writeTo(channel);
        } catch (IOException e) {
            Log.e(TAG, "Cannot write", e);
            return false;
        }
    }

    private void pushToClient() {
        assert pendingDatagramForClient;
        IPv4Packet packet = IPv4Packet.merge(responseIPv4Header, responseUDPHeader, networkToClient);
        packet.recompute();
        if (sendToClient(packet)) {
            Log.d(TAG, "PACKET SEND TO CLIENT");
            pendingDatagramForClient = false;
            networkToClient.clear();
        }
    }

    protected void updateInterests() {
        int interestingOps = 0;
        if (!pendingDatagramForClient) {
            interestingOps |= SelectionKey.OP_READ;
        } else {
            Log.d(TAG, "DISABLE READ");
        }
        if (hasPendingWrites()) {
            interestingOps |= SelectionKey.OP_WRITE;
        }
        selectionKey.interestOps(interestingOps);
    }
}
