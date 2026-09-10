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

import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Wrapper for writing one IP packet at a time to an [OutputStream].
 */
class IPPacketOutputStream(private val target: OutputStream) : OutputStream() {
    companion object {
        private val TAG = DLog.forTag(IPPacketOutputStream::class.java)

        private val MAX_IP_PACKET_LENGTH = 1 shl 16 // packet length is stored on 16 bits

        /**
         * Read the packet IP version, assuming that an IP packets is stored at absolute position 0.
         *
         * @param buffer the buffer
         * @return the IP version, or `-1` if not available
         */
        fun readPacketVersion(buffer: ByteBuffer): Int {
            if (!buffer.hasRemaining()) {
                // buffer is empty
                return -1
            }
            // version is stored in the 4 first bits
            val versionAndIHL = buffer.get(buffer.position())
            return (versionAndIHL.toInt() and 0xf0) shr 4
        }

        /**
         * Read the packet length, assuming thatan IP packet is stored at absolute position 0.
         *
         * @param buffer the buffer
         * @return the packet length, or `-1` if not available
         */
        fun readPacketLength(buffer: ByteBuffer): Int {
            if (buffer.limit() < buffer.position() + 4) {
                // buffer does not even contains the length field
                return -1
            }
            // packet length is 16 bits starting at offset 2
            return Binary.unsigned(buffer.getShort(buffer.position() + 2))
        }
    }

    // must always accept 1 full packet + any partial packet
    private val buffer: ByteBuffer = ByteBuffer.allocate(2 * MAX_IP_PACKET_LENGTH)

    @Throws(IOException::class)
    override fun close() {
        target.close()
    }

    @Throws(IOException::class)
    override fun flush() {
        target.flush()
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray?, off: Int, len: Int) {
        if (len > MAX_IP_PACKET_LENGTH) {
            throw IOException("IPPacketOutputStream does not support writing more than one packet at a time")
        }
        // by design, the buffer must always have enough space for one packet
        if (BuildConfig.DEBUG && len > buffer.remaining()) {
            DLog.error(TAG, len.toString() + " must be <= than " + buffer.remaining())
            DLog.error(TAG, buffer.toString())
            throw AssertionError("Buffer is unexpectedly full")
        }
        buffer.put(b, off, len)
        buffer.flip()
        sink()
        buffer.compact()
    }

    @Throws(IOException::class)
    override fun write(b: Int) {
        if (!buffer.hasRemaining()) {
            throw IOException("IPPacketOutputStream buffer is full")
        }
        buffer.put(b.toByte())
        buffer.flip()
        sink()
        buffer.compact()
    }

    @Throws(IOException::class)
    private fun sink() {
        // sink all packets
        while (sinkPacket()) {
            // continue
        }
    }

    @Throws(IOException::class)
    private fun sinkPacket(): Boolean {
        val version = readPacketVersion(buffer)
        if (version == -1) {
            // no packet at all
            return false
        }
        if (version != 4) {
            DLog.error(TAG, "Unsupported packet received, IP version is: $version")
            DLog.error(TAG, "Clearing buffer")
            buffer.clear()
            return false
        }
        val packetLength = readPacketLength(buffer)
        if (packetLength == -1 || packetLength > buffer.remaining()) {
            // no packet
            return false
        }

        target.write(buffer.array(), buffer.arrayOffset() + buffer.position(), packetLength)
        buffer.position(buffer.position() + packetLength)
        return true
    }
}