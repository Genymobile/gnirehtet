package com.genymobile.gnirehtet

import android.os.Handler

/**
 * Convenient wrapper to dispatch events to the given [Handler].
 */
class RelayTunnelListener(private val handler: Handler) {
    fun notifyRelayTunnelConnected() {
        handler.sendEmptyMessage(MSG_RELAY_TUNNEL_CONNECTED)
    }

    fun notifyRelayTunnelDisconnected() {
        handler.sendEmptyMessage(MSG_RELAY_TUNNEL_DISCONNECTED)
    }

    companion object {
        const val MSG_RELAY_TUNNEL_CONNECTED: Int = 0
        const val MSG_RELAY_TUNNEL_DISCONNECTED: Int = 1
    }
}