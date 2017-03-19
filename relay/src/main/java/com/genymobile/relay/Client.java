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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Client {

    private static final String TAG = Client.class.getSimpleName();

    private final SocketChannel clientChannel;
    private final SelectionKey selectionKey;
    private final RemoveHandler<Client> removeHandler;

    private final IPv4PacketBuffer clientToNetwork = new IPv4PacketBuffer();
    private final StreamBuffer networkToClient = new StreamBuffer(16 * IPv4Packet.MAX_PACKET_LENGTH);
    private final Router router;

    private final List<PacketSource> pendingPacketSources = new ArrayList<>();

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
        // on start, we are interested only in reading (there is nothing to write)
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
        processPending();
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
        int interestingOps = SelectionKey.OP_READ; // we always want to read
        if (!networkToClient.isEmpty()) {
            interestingOps |= SelectionKey.OP_WRITE;
        }
        selectionKey.interestOps(interestingOps);
    }

    public boolean sendToClient(IPv4Packet packet) {
        if (networkToClient.remaining() < packet.getRawLength()) {
            Log.w(TAG, "Client buffer full, delaying packet processing");
            return false;
        }
        networkToClient.readFrom(packet.getRaw());
        updateInterests();
        return true;
    }

    public void consume(PacketSource source) {
        IPv4Packet packet = source.get();
        if (sendToClient(packet)) {
            source.next();
            return;
        }
        assert !pendingPacketSources.contains(source);
        pendingPacketSources.add(source);
    }

    private void processPending() {
        Iterator<PacketSource> iterator = pendingPacketSources.iterator();
        while (iterator.hasNext()) {
            PacketSource packetSource = iterator.next();
            IPv4Packet packet = packetSource.get();
            if (sendToClient(packet)) {
                packetSource.next();
                Log.d(TAG, "Pending packet sent to client (" + packet.getRawLength() + ")");
                iterator.remove();
            } else {
                Log.w(TAG, "Pending packet not sent to client (" + packet.getRawLength() + "), client buffer full again");
                return;
            }
        }
    }

    public void cleanExpiredConnections() {
        router.cleanExpiredConnections();
    }
}
