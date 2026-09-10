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

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.net.VpnService
import com.genymobile.gnirehtet.Binary.buildPacketString
import com.genymobile.gnirehtet.Binary.unsigned
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream

class RelayTunnel private constructor() : Tunnel {
    companion object {
        private val TAG = DLog.forTag(RelayTunnel::class.java)

        private const val LOCAL_ABSTRACT_NAME = "gnirehtet"

        //@Suppress("unused")
        @JvmStatic
        @Throws(IOException::class)
        fun open(vpnService: VpnService?): RelayTunnel {
            DLog.debug(TAG, "Opening a new relay tunnel...")
            // since we use a local socket, we don't need to protect the socket from the vpnService anymore
            // but this is an implementation detail, so keep the method signature
            return RelayTunnel()
        }

        /**
         * The relay server is accessible through an "adb reverse" port redirection.
         *
         *
         * If the port redirection is enabled but the relay server is not started, then the call to
         * channel.connect() will succeed, but the first read() will return -1.
         *
         *
         * As a consequence, the connection state of the relay server would be invalid temporarily (we
         * would switch to CONNECTED state then switch back to DISCONNECTED).
         *
         *
         * To avoid this problem, we must actually read from the server, so that an error occurs
         * immediately if the relay server is not accessible.
         *
         *
         * Therefore, the relay server immediately sends the client id: consume it and log it.
         *
         * @param inputStream the input stream to receive data from the relay server
         * @throws IOException if an I/O error occurs
         */
        @Throws(IOException::class)
        private fun readClientId(inputStream: InputStream) {
            DLog.debug(TAG, "Requesting client id")
            val clientId = DataInputStream(inputStream).readInt()
            DLog.debug(TAG, "Connected to the relay server as #" + unsigned(clientId))
        }

    }

    private val localSocket = LocalSocket()

    @Throws(IOException::class)
    fun connect() {
        localSocket.connect(LocalSocketAddress(LOCAL_ABSTRACT_NAME))
        readClientId(localSocket.getInputStream())
    }

    @Throws(IOException::class)
    override fun send(packet: ByteArray?, len: Int) {
        DLog.verbose(TAG, "Sending packet: " + buildPacketString(packet, len))
        localSocket.getOutputStream().write(packet, 0, len)
    }

    @Throws(IOException::class)
    override fun receive(packet: ByteArray?): Int {
        val r = localSocket.getInputStream().read(packet)
        DLog.verbose(TAG, "Receiving packet: " + buildPacketString(packet, r))
        return r
    }

    override fun close() {
        try {
            if (localSocket.getFileDescriptor() != null) {
                // close the streams to interrupt pending read and writes
                localSocket.shutdownInput()
                localSocket.shutdownOutput()
            }
            localSocket.close()
        } catch (e: IOException) {
            // what could we do?
            throw RuntimeException(e)
        }
    }
}