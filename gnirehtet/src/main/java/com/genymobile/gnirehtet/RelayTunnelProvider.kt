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
package com.genymobile.gnirehtet

import android.net.VpnService
import com.genymobile.gnirehtet.RelayTunnel.Companion.open
import java.io.IOException

/**
 * Provide a valid [RelayTunnel], creating a new one if necessary.
 */
class RelayTunnelProvider(
    private val vpnService: VpnService?,
    private val listener: RelayTunnelListener?
) {
    private val getCurrentTunnelLock = Any() // protects getCurrentTunnel()

    private var tunnel: RelayTunnel? = null // protected both by "this" and "getCurrentTunnelLock"
    private var first = true // protected by "getCurrentTunnelLock"
    private var lastFailureTimestamp: Long = 0 // protected by "this"

    @get:Throws(IOException::class, InterruptedException::class)
    val currentTunnel: RelayTunnel?
        get() {
            /*
              * To make sure that both the sending and receiving threads use the same tunnel, we must
              * guarantee that this method may not be called several times concurrently.
              *
              * However, since it executes potentially long-running blocking calls, we still want to be
              * able to call invalidateTunnel() concurrently, which requires to protect some fields.
              *
              * Therefore, use one mutex ("getCurrentTunnelLock") to avoid concurrent calls to
              * getCurrentTunnel(), and another one ("this") to protect fields shared with
              * invalidateTunnel().
              */
            synchronized(getCurrentTunnelLock) {
                synchronized(this) {
                    if (tunnel != null) {
                        return tunnel
                    }
                    waitUntilNextAttemptSlot()

                    // "tunnel" has not changed during waiting (only getCurrentTunnel() may write it)
                    tunnel = open(vpnService)
                }
                // the first connection must either notify "connected" or "disconnected"
                val notifyDisconnectedOnError = first
                first = false
                connectTunnel(notifyDisconnectedOnError)
            }
            return tunnel
        }

    @Throws(IOException::class)
    private fun connectTunnel(notifyDisconnectedOnError: Boolean) {
        try {
            tunnel!!.connect()
            notifyConnected()
        } catch (e: IOException) {
            touchFailure()
            if (notifyDisconnectedOnError) {
                notifyDisconnected()
            }
            throw e
        }
    }

    @Synchronized
    fun invalidateTunnel() {
        if (tunnel != null) {
            touchFailure()
            tunnel!!.close()
            tunnel = null
            notifyDisconnected()
        }
    }

    /**
     * Call [.invalidateTunnel] only if `tunnelToInvalidate` is the current tunnel (or
     * is `null`).
     *
     * @param tunnelToInvalidate the tunnel to invalidate
     */
    @Synchronized
    fun invalidateTunnel(tunnelToInvalidate: Tunnel?) {
        if (tunnel == tunnelToInvalidate || tunnelToInvalidate == null) {
            invalidateTunnel()
        }
    }

    @Synchronized
    private fun touchFailure() {
        lastFailureTimestamp = System.currentTimeMillis()
    }

    @Throws(InterruptedException::class)
    private fun waitUntilNextAttemptSlot() {
        if (first) {
            // do not wait on first attempt
            return
        }
        var delay = lastFailureTimestamp + DELAY_BETWEEN_ATTEMPTS_MS - System.currentTimeMillis()
        while (delay > 0) {
            (this as Object).wait(delay)
            delay = lastFailureTimestamp + DELAY_BETWEEN_ATTEMPTS_MS - System.currentTimeMillis()
        }
    }

    private fun notifyConnected() {
        if (listener != null) {
            listener.notifyRelayTunnelConnected()
        }
    }

    private fun notifyDisconnected() {
        if (listener != null) {
            listener.notifyRelayTunnelDisconnected()
        }
    }

    companion object {
        private const val DELAY_BETWEEN_ATTEMPTS_MS = 5000
    }
}