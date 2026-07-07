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
import java.util.Set;

public class Relay {

    private static final String TAG = Relay.class.getSimpleName();

    private static final int CLEANING_INTERVAL = 60 * 1000;

    private final int port;
    private final TrafficStats trafficStats;
    private final Runnable startedListener;

    private volatile boolean stopped;
    private Selector selector;

    public Relay(int port) {
        this(port, new TrafficStats(), null);
    }

    public Relay(int port, TrafficStats trafficStats) {
        this(port, trafficStats, null);
    }

    public Relay(int port, TrafficStats trafficStats, Runnable startedListener) {
        this.port = port;
        this.trafficStats = trafficStats;
        this.startedListener = startedListener;
    }

    public void run() throws IOException {
        selector = Selector.open();

        // will register the socket on the selector
        TunnelServer tunnelServer = new TunnelServer(port, selector, trafficStats);

        Log.i(TAG, "Relay server started");
        notifyStarted();

        try {
            long nextCleaningDeadline = System.currentTimeMillis() + UDPConnection.IDLE_TIMEOUT;
            while (!stopped) {
                long timeout = Math.max(0, nextCleaningDeadline - System.currentTimeMillis());
                selector.select(timeout);
                Set<SelectionKey> selectedKeys = selector.selectedKeys();

                long now = System.currentTimeMillis();
                if (now >= nextCleaningDeadline || selectedKeys.isEmpty()) {
                    tunnelServer.cleanUp();
                    nextCleaningDeadline = now + CLEANING_INTERVAL;
                }

                for (SelectionKey selectedKey : selectedKeys) {
                    SelectionHandler selectionHandler = (SelectionHandler) selectedKey.attachment();
                    selectionHandler.onReady(selectedKey);
                }
                // by design, we handled everything
                selectedKeys.clear();
            }
        } finally {
            tunnelServer.close();
            selector.close();
            selector = null;
            Log.i(TAG, "Relay server stopped");
        }
    }

    public void stop() {
        stopped = true;
        Selector currentSelector = selector;
        if (currentSelector != null) {
            currentSelector.wakeup();
        }
    }

    private void notifyStarted() {
        if (startedListener != null) {
            startedListener.run();
        }
    }
}
