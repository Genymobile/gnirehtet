package com.genymobile.relay;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class Client {

    private static final String TAG = Client.class.getSimpleName();

    private final SocketChannel clientChannel;
    private final SelectionKey selectionKey;
    private final RemoveHandler<Client> removeHandler;

    private final IPv4PacketBuffer clientToNetwork = new IPv4PacketBuffer();
    private final StreamBuffer networkToClient = new StreamBuffer(16 * IPv4Packet.MAX_PACKET_LENGTH);
    private final Router router;

    public Client(Selector selector, SocketChannel clientChannel, RemoveHandler<Client> removeHandler) throws ClosedChannelException {
        this.clientChannel = clientChannel;
        router = new Router(this, selector);

        SelectionHandler selectionHandler = (selectionKey) -> {
            if (selectionKey.isValid() && selectionKey.isWritable()) {
                processSend();
            }
            if (selectionKey.isValid() && selectionKey.isReadable()) {
                processReceive();
            }
            if (selectionKey.isValid()) {
                updateInterests();
            }
        };
        // on start, we are interested only in reading (there is nothing to onWritable)
        selectionKey = clientChannel.register(selector, SelectionKey.OP_READ, selectionHandler);

        this.removeHandler = removeHandler;
    }

    private void processReceive() {
        if (!read()) {
            destroy();
            return;
        }
        pushToNetwork();
    }

    private void processSend() {
        if (!write()) {
            destroy();
            return;
        }
    }

    private boolean read() {
        try {
            return clientToNetwork.readFrom(clientChannel) != -1;
        } catch (IOException e) {
            Log.e(TAG, "Cannot read", e);
            return false;
        }
    }

    private boolean write() {
        try {
            return networkToClient.writeTo(clientChannel) != -1;
        } catch (IOException e) {
            Log.e(TAG, "Cannot write", e);
            return false;
        }
    }

    private void pushToNetwork() {
        IPv4Packet packet;
        while ((packet = clientToNetwork.asIPv4Packet()) != null) {
            router.sendToNetwork(packet);
            clientToNetwork.next();
        }
    }

    private void destroy() {
        selectionKey.cancel();
        try {
            clientChannel.close();
        } catch (IOException e) {
            Log.e(TAG, "Cannot close client connection", e);
        }
        router.clear();
        removeHandler.remove(this);
    }

    private void updateInterests() {
        int interestingOps = SelectionKey.OP_READ; // we always want to onReadable
        if (!networkToClient.isEmpty()) {
            interestingOps |= SelectionKey.OP_WRITE;
        }
        selectionKey.interestOps(interestingOps);
    }

    public boolean sendToClient(IPv4Packet packet) {
        if (networkToClient.remaining() < packet.getRawLength()) {
            // FIXME some parts of the app assume that a packet to the client is never lost
            Log.e(TAG, "************ COMMUNICATION BROKEN **********");
            return false;
        }
        networkToClient.readFrom(packet.getRaw());
        updateInterests();
        return true;
    }

    public void cleanExpiredConnections() {
        router.cleanExpiredConnections();
    }
}
