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

import java.net.InetAddress;
import java.net.InetSocketAddress;

public abstract class AbstractConnection implements Connection {

    private static final String TAG = AbstractConnection.class.getSimpleName();

    private static final int LOCALHOST_FORWARD = 0x0a000202; // 10.0.2.2 must be forwarded to localhost

    private final Route route;

    protected AbstractConnection(Route route) {
        this.route = route;
    }

    protected void destroy() {
        Log.i(TAG, route.getKey() + " Close");

        // remove the route from the router
        route.discard();

        // close and unregister the channel
        disconnect();
    }

    protected void consume(PacketSource source) {
        route.consume(source);
    }

    protected boolean sendToClient(IPv4Packet packet) {
        return route.sendToClient(packet);
    }

    private static InetAddress getRewrittenAddress(int ip) {
        return ip == LOCALHOST_FORWARD ? InetAddress.getLoopbackAddress() : Net.toInetAddress(ip);
    }

    /**
     * Get destination, rewritten to {@code localhost} if it was {@code 10.0.2.2}.
     *
     * @return Destination to connect to.
     */
    protected InetSocketAddress getRewrittenDestination() {
        Route.Key key = route.getKey();
        int destIp = key.getDestinationIp();
        int port = key.getDestinationPort();
        return new InetSocketAddress(getRewrittenAddress(destIp), port);
    }

    public void logv(String tag, String message, Throwable e) {
        Log.v(tag, route.getKey() + " " + message);
    }

    public void logv(String tag, String message) {
        logv(tag, message, null);
    }

    public void logd(String tag, String message, Throwable e) {
        Log.d(tag, route.getKey() + " " + message);
    }

    public void logd(String tag, String message) {
        logd(tag, message, null);
    }

    public void logi(String tag, String message, Throwable e) {
        Log.i(tag, route.getKey() + " " + message);
    }

    public void logi(String tag, String message) {
        logi(tag, message, null);
    }

    public void logw(String tag, String message, Throwable e) {
        Log.w(tag, route.getKey() + " " + message);
    }

    public void logw(String tag, String message) {
        logw(tag, message, null);
    }

    public void loge(String tag, String message, Throwable e) {
        Log.e(tag, route.getKey() + " " + message);
    }

    public void loge(String tag, String message) {
        loge(tag, message, null);
    }
}
