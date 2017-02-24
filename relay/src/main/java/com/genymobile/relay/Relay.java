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

    private static final int DEFAULT_PORT = 1080;

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

            if (selectorAlarm.accept()) {
                cleanUp();
            }

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            for (SelectionKey selectedKey : selectedKeys) {
                SelectionHandler selectionHandler = (SelectionHandler) selectedKey.attachment();
                //Log.d(TAG, "selected… " + selectedKey.readyOps());
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
        clients.add(new Client(selector, socketChannel, this::removeClient));
    }

    private void removeClient(Client client) {
        clients.remove(client);
    }

    private void cleanUp() {
        for (Client client : clients) {
            client.cleanExpiredConnections();
        }
    }

    public static void main(String... args) throws IOException {
        Log.i(TAG, "Starting server...");
        new Relay().start();
    }
}
