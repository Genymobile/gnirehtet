package com.genymobile.relay;

public abstract class AbstractConnection implements Connection {

    private static final String TAG = AbstractConnection.class.getName();

    protected final Route route;

    protected AbstractConnection(Route route) {
        this.route = route;
    }

    protected void destroy() {
        Log.d(TAG, "destroy()");

        // remove the route from the router
        route.discard();

        // close and unregister the channel
        disconnect();
    }

    protected void sendToClient(IPv4Packet packet) {
        route.sendToClient(packet);
    }
}
