package com.genymobile.relay;

public abstract class AbstractConnection implements Connection {

    private static final String TAG = AbstractConnection.class.getSimpleName();

    protected final Route route;

    protected AbstractConnection(Route route) {
        this.route = route;
    }

    protected void destroy() {
        Log.i(TAG, route.getKey() + " destroy()");

        // remove the route from the router
        route.discard();

        // close and unregister the channel
        disconnect();
    }

    protected boolean sendToClient(IPv4Packet packet) {
        return route.sendToClient(packet);
    }
}
