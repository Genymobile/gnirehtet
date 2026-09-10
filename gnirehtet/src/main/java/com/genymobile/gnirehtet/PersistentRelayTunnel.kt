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
import android.util.Log
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Expose a [TunnelJ] that automatically handles [RelayTunnelJ] reconnections.
 */
class PersistentRelayTunnel(vpnService: VpnService?, listener: RelayTunnelListener?) : Tunnel {
    companion object {
        private val TAG =DLog.forTag(PersistentRelayTunnel::class.java)
    }
    private val provider: RelayTunnelProvider
    private val stopped = AtomicBoolean()

    init {
        provider = RelayTunnelProvider(vpnService, listener)
    }

    @Throws(IOException::class)
    public override fun send(packet: ByteArray?, len: Int) {
        while (!stopped.get()) {
            var tunnel: Tunnel? = null
            try {
                tunnel = provider.currentTunnel
                tunnel?.send(packet, len)
                return
            } catch (e: IOException) {
                Log.e(TAG, "Cannot send to tunnel", e)
                if (tunnel != null) {
                    provider.invalidateTunnel(tunnel)
                }
            } catch (e: InterruptedException) {
                Log.e(TAG, "Cannot send to tunnel", e)
                if (tunnel != null) {
                    provider.invalidateTunnel(tunnel)
                }
            }
        }
        throw InterruptedIOException("Persistent tunnel stopped")
    }

    @Throws(IOException::class)
    public override fun receive(packet: ByteArray?): Int {
        while (!stopped.get()) {
            var tunnel: Tunnel? = null
            try {
                tunnel = provider.currentTunnel
                val r = tunnel?.receive(packet)
                if (r == -1) {
                    Log.d(TAG, "Tunnel read EOF")
                    provider.invalidateTunnel(tunnel)
                    continue
                }
                return r!!
            } catch (e: IOException) {
                Log.e(TAG, "Cannot receive from tunnel", e)
                if (tunnel != null) {
                    provider.invalidateTunnel(tunnel)
                }
            } catch (e: InterruptedException) {
                Log.e(TAG, "Cannot receive from tunnel", e)
                if (tunnel != null) {
                    provider.invalidateTunnel(tunnel)
                }
            }
        }
        throw InterruptedIOException("Persistent tunnel stopped")
    }

    public override fun close() {
        stopped.set(true)
        provider.invalidateTunnel()
    }
}