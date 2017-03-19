package com.genymobile.relay;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public abstract class AbstractConnection implements Connection {

    private static final String TAG = AbstractConnection.class.getSimpleName();

    private static final int LOCALHOST_FORWARD = 0x0a000202; // 10.0.2.2 must be forwarded to localhost

    protected final Route route;

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
}
