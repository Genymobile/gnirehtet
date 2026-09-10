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

import kotlin.math.min

object Binary {
    private const val MAX_STRING_PACKET_SIZE = 20

    fun unsigned(value: Byte): Int {
        return value.toInt() and 0xff
    }

    @JvmStatic
    fun unsigned(value: Short): Int {
        return value.toInt() and 0xffff
    }

    @JvmStatic
    fun unsigned(value: Int): Long {
        return value.toLong() and 0xffffffffL
    }

    @JvmStatic
    fun buildPacketString(data: ByteArray?, len: Int): String {
        val limit = min(MAX_STRING_PACKET_SIZE, len)
        val builder = StringBuilder()
        builder.append('[').append(len).append(" bytes] ")
        for (i in 0..<limit) {
            if (i != 0) {
                val sep = if (i % 4 == 0) "  " else " "
                builder.append(sep)
            }
            builder.append(String.format("%02X", data?.get(i)?.toInt()?.and(0xff)))
        }
        if (limit < len) {
            builder.append(" ... +").append(len - limit).append(" bytes")
        }
        return builder.toString()
    }
}