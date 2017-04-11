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
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Relay {

    private static final String TAG = Relay.class.getSimpleName();

    private static final int DEFAULT_PORT = 31416;

    private int port;
    private final List<Client> clients = new ArrayList<>();

    public Relay() {
        this(DEFAULT_PORT);
    }

    public Relay(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        Selector selector = Selector.open();

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        // ServerSocketChannel.bind() requires API 24
        serverSocketChannel.socket().bind(new InetSocketAddress(Inet4Address.getLoopbackAddress(), port));

        SelectionHandler socketChannelHandler = (selectionKey) -> {
            try {
                ServerSocketChannel channel = (ServerSocketChannel) selectionKey.channel();
                acceptClient(selector, channel);
            } catch (IOException e) {
                Log.e(TAG, "Cannot accept client", e);
            }
        };
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT, socketChannelHandler);

        SelectorAlarm selectorAlarm = new SelectorAlarm(selector);
        selectorAlarm.start();

        while (true) {
            selector.select();
            Set<SelectionKey> selectedKeys = selector.selectedKeys();

            if (selectorAlarm.accept()) {
                cleanUp();
            } else if (selectedKeys.isEmpty()) {
                throw new AssertionError("selector.select() returned without any event, an invalid SelectionKey was probably been registered");
            }

            for (SelectionKey selectedKey : selectedKeys) {
                SelectionHandler selectionHandler = (SelectionHandler) selectedKey.attachment();
                selectionHandler.onReady(selectedKey);
            }
            // by design, we handled everything
            selectedKeys.clear();
        }
    }

    private void acceptClient(Selector selector, ServerSocketChannel serverSocketChannel) throws IOException {
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);
        // will register the socket on the selector
        Client client = new Client(selector, socketChannel, this::removeClient);
        clients.add(client);
        Log.i(TAG, "Client #" + client.getId() + " connected");
    }

    private void removeClient(Client client) {
        clients.remove(client);
        Log.i(TAG, "Client #" + client.getId() + " disconnected");
    }

    private void cleanUp() {
        for (Client client : clients) {
            client.cleanExpiredConnections();
        }
    }
}
