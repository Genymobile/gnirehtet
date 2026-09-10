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
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class Forwarder(
    vpnService: VpnService?,
    private val vpnFileDescriptor: FileDescriptor,
    listener: RelayTunnelListener?
) {
    companion object {
        private val TAG = DLog.forTag(Forwarder::class.java)

        private val EXECUTOR_SERVICE: ExecutorService = Executors.newFixedThreadPool(3)


        private const val BUFSIZE = 0x10000

        private val DUMMY_ADDRESS = byteArrayOf(42, 42, 42, 42)
        private const val DUMMY_PORT = 4242
    }
    private val tunnel: PersistentRelayTunnel

    private var deviceToTunnelFuture: Future<*>? = null
    private var tunnelToDeviceFuture: Future<*>? = null

    init {
        tunnel = PersistentRelayTunnel(vpnService, listener)
    }

    fun forward() {
        deviceToTunnelFuture = EXECUTOR_SERVICE.submit(object : Runnable {
            override fun run() {
                try {
                    forwardDeviceToTunnel(tunnel)
                } catch (e: InterruptedIOException) {
                    DLog.debug(TAG, "Device to tunnel interrupted")
                } catch (e: IOException) {
                    DLog.exception(TAG, "Device to tunnel exception", e)
                }
            }
        })
        tunnelToDeviceFuture = EXECUTOR_SERVICE.submit(object : Runnable {
            override fun run() {
                try {
                    forwardTunnelToDevice(tunnel)
                } catch (e: InterruptedIOException) {
                    DLog.debug(TAG, "Device to tunnel interrupted")
                } catch (e: IOException) {
                    DLog.exception(TAG, "Tunnel to device exception", e)
                }
            }
        })
    }

    fun stop() {
        tunnel.close()
        tunnelToDeviceFuture!!.cancel(true)
        deviceToTunnelFuture!!.cancel(true)
        wakeUpReadWorkaround()
    }

    @Throws(IOException::class)
    private fun forwardDeviceToTunnel(tunnel: Tunnel) {
        DLog.debug(TAG, "Device to tunnel forwarding started")
        val vpnInput = FileInputStream(vpnFileDescriptor)
        val buffer = ByteArray(BUFSIZE)
        while (true) {
            // blocking read
            val r = vpnInput.read(buffer)
            if (r == -1) {
                DLog.debug(TAG, "VPN closed")
                break
            }
            if (r > 0) {
                val version = buffer[0].toInt() shr 4
                if (version == 4) {
                    // blocking send
                    tunnel.send(buffer, r)
                } else {
                    // see <https://github.com/Genymobile/gnirehtet/issues/69>
                    DLog.warning(TAG, "Unexpected packet IP version: " + version)
                }
            } else {
                DLog.debug(TAG, "Empty read")
            }
        }
        DLog.debug(TAG, "Device to tunnel forwarding stopped")
    }

    @Throws(IOException::class)
    private fun forwardTunnelToDevice(tunnel: Tunnel) {
        DLog.debug(TAG, "Tunnel to device forwarding started")
        val vpnOutput = FileOutputStream(vpnFileDescriptor)
        val packetOutputStream = IPPacketOutputStream(vpnOutput)

        val buffer = ByteArray(BUFSIZE)
        while (true) {
            // blocking receive
            val w = tunnel.receive(buffer)
            if (w == -1) {
                DLog.debug(TAG, "Tunnel closed")
                break
            }
            if (w > 0) {
                // blocking write
                packetOutputStream.write(buffer, 0, w)
            } else {
                DLog.debug(TAG, "Empty write")
            }
        }
        DLog.debug(TAG, "Tunnel to device forwarding stopped")
    }

    /**
     * Neither vpnInterface.close() nor vpnInputStream.close() wake up a blocking
     * vpnInputStream.read().
     *
     *
     * Therefore, we need to make Android send a packet to the VPN interface (here by sending a UDP
     * packet), so that any blocking read will be woken up.
     *
     *
     * Since the tunnel is closed at this point, it will never reach the network.
     */
    private fun wakeUpReadWorkaround() {
        // network actions may not be called from the main thread
        EXECUTOR_SERVICE.execute(object : Runnable {
            override fun run() {
                try {
                    val socket = DatagramSocket()
                    val dummyAddr = InetAddress.getByAddress(DUMMY_ADDRESS)
                    val packet = DatagramPacket(ByteArray(0), 0, dummyAddr, DUMMY_PORT)
                    socket.send(packet)
                } catch (e: IOException) {
                    // ignore
                }
            }
        })
    }

}