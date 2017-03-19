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
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.List;

public class Router {

    private static final String TAG = Router.class.getSimpleName();

    private final Client client;
    private final Selector selector;

    // there are typically only few connections per client, HashMap would be less efficient
    private final List<Route> routes = new ArrayList<>();

    public Router(Client client, Selector selector) {
        this.client = client;
        this.selector = selector;
    }

    public void sendToNetwork(IPv4Packet packet) {
        if (!packet.isValid()) {
            Log.w(TAG, "Dropping invalid packet");
            if (Log.isVerboseEnabled()) {
                Log.v(TAG, String.valueOf(packet.getRaw()));
            }
            return;
        }
        try {
            Route route = getRoute(packet.getIpv4Header(), packet.getTransportHeader());
            route.sendToNetwork(packet);
        } catch (IOException e) {
            Log.e(TAG, "Cannot create route, dropping packet", e);
            return;
        }
    }

    private Route getRoute(IPv4Header ipv4Header, TransportHeader transportHeader) throws IOException {
        Route.Key key = Route.getKey(ipv4Header, transportHeader);
        Route route = findRoute(key);
        if (route == null) {
            route = new Route(client, selector, key, ipv4Header, transportHeader, this::removeRoute);
            routes.add(route);
        }
        return route;
    }

    private int findRouteIndex(Route.Key key) {
        for (int i = 0; i < routes.size(); ++i) {
            Route route = routes.get(i);
            if (key.equals(route.getKey())) {
                return i;
            }
        }
        return -1;
    }

    private Route findRoute(Route.Key key) {
        int routeIndex = findRouteIndex(key);
        if (routeIndex == -1) {
            return null;
        }
        return routes.get(routeIndex);
    }

    public void clear() {
        for (Route route : routes) {
            route.disconnect();
        }
        // optimization of route.discard() for all routes
        routes.clear();
    }

    public boolean removeRoute(Route.Key key) {
        int routeIndex = findRouteIndex(key);
        if (routeIndex == -1) {
            return false;
        }
        routes.remove(routeIndex);
        return true;
    }

    public void cleanExpiredConnections() {
        for (int i = routes.size() - 1; i >= 0; --i) {
            Route route = routes.get(i);
            if (route.isConnectionExpired()) {
                Log.d(TAG, "Remove expired connection: " + route.getKey());
                route.disconnect();
                routes.remove(i);
            }
        }
    }
}
