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
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

public class UDPConnection extends AbstractConnection {

    public static final long IDLE_TIMEOUT = 2 * 60 * 1000;

    private static final String TAG = UDPConnection.class.getSimpleName();

    private final DatagramBuffer clientToNetwork = new DatagramBuffer(4 * IPv4Packet.MAX_PACKET_LENGTH);
    private final Packetizer networkToClient;

    private final DatagramChannel channel;
    private final SelectionKey selectionKey;

    private long idleSince;

    public UDPConnection(ConnectionId id, Client client, Selector selector, IPv4Header ipv4Header, UDPHeader udpHeader) throws IOException {
        super(id, client);

        networkToClient = new Packetizer(ipv4Header, udpHeader);
        networkToClient.getResponseIPv4Header().swapSourceAndDestination();
        networkToClient.getResponseTransportHeader().swapSourceAndDestination();

        touch();

        SelectionHandler selectionHandler = (selectionKey) -> {
            touch();
            if (selectionKey.isValid() && selectionKey.isReadable()) {
                processReceive();
            }
            if (selectionKey.isValid() && selectionKey.isWritable()) {
                processSend();
            }
            updateInterests();
        };
        channel = createChannel();
        selectionKey = channel.register(selector, SelectionKey.OP_READ, selectionHandler);
    }

    @Override
    public void sendToNetwork(IPv4Packet packet) {
        if (!clientToNetwork.readFrom(packet.getPayload())) {
            logw(TAG, "Cannot send to network, dropping packet");
            return;
        }
        updateInterests();
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

    @Override
    public boolean isExpired() {
        return System.currentTimeMillis() >= idleSince + IDLE_TIMEOUT;
    }

    private DatagramChannel createChannel() throws IOException {
        logi(TAG, "Open");
        DatagramChannel datagramChannel = DatagramChannel.open();
        datagramChannel.configureBlocking(false);
        datagramChannel.connect(getRewrittenDestination());
        return datagramChannel;
    }

    private void touch() {
        idleSince = System.currentTimeMillis();
    }

    private void processReceive() {
        IPv4Packet packet = read();
        if (packet == null) {
            close();
            return;
        }
        pushToClient(packet);
    }

    private void processSend() {
        if (!write()) {
            close();
        }
    }

    private IPv4Packet read() {
        try {
            return networkToClient.packetize(channel);
        } catch (IOException e) {
            loge(TAG, "Cannot read", e);
            return null;
        }
    }

    private boolean write() {
        try {
            return clientToNetwork.writeTo(channel);
        } catch (IOException e) {
            loge(TAG, "Cannot write", e);
            return false;
        }
    }

    private void pushToClient(IPv4Packet packet) {
        if (!sendToClient(packet)) {
            logw(TAG, "Cannot send to client, dropping packet");
            return;
        }
        logd(TAG, "Packet (" + packet.getPayloadLength() + " bytes) sent to client");
        if (Log.isVerboseEnabled()) {
            logv(TAG, Binary.toString(packet.getRaw()));
        }
    }

    protected void updateInterests() {
        if (!selectionKey.isValid()) {
            return;
        }
        int interestingOps = SelectionKey.OP_READ;
        if (mayWrite()) {
            interestingOps |= SelectionKey.OP_WRITE;
        }
        selectionKey.interestOps(interestingOps);
    }

    private boolean mayWrite() {
        return !clientToNetwork.isEmpty();
    }
}
