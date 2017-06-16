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
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;

public class Relay {

    private static final int DEFAULT_PORT = 31416;
    private static final int CLEANING_INTERVAL = 60 * 1000;

    private final int port;

    public Relay() {
        this(DEFAULT_PORT);
    }

    public Relay(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        Selector selector = Selector.open();

        // will register the socket on the selector
        TunnelServer tunnelServer = new TunnelServer(port, selector);

        long nextCleaningDeadline = System.currentTimeMillis() + UDPConnection.IDLE_TIMEOUT;
        while (true) {
            long timeout = Math.max(0, nextCleaningDeadline - System.currentTimeMillis());
            selector.select(timeout);
            Set<SelectionKey> selectedKeys = selector.selectedKeys();

            long now = System.currentTimeMillis();
            if (now >= nextCleaningDeadline) {
                tunnelServer.cleanUp();
                nextCleaningDeadline = now + CLEANING_INTERVAL;
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
}
