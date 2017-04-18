package com.genymobile.gnirehtet;

import android.os.Handler;

/**
 * Convenient wrapper to dispatch events to the given {@link Handler}.
 */
public class RelayTunnelListener {

    static final int MSG_RELAY_TUNNEL_CONNECTED = 0;
    static final int MSG_RELAY_TUNNEL_DISCONNECTED = 1;

    private final Handler handler;

    public RelayTunnelListener(Handler handler) {
        this.handler = handler;
    }

    public void notifyRelayTunnelConnected() {
        handler.sendEmptyMessage(MSG_RELAY_TUNNEL_CONNECTED);
    }

    public void notifyRelayTunnelDisconnected() {
        handler.sendEmptyMessage(MSG_RELAY_TUNNEL_DISCONNECTED);
    }
}
