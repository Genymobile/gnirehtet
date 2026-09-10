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

import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException

object Net {
    fun toInetAddresses(vararg addresses: String?): Array<InetAddress?> {
        val result = arrayOfNulls<InetAddress>(addresses.size)
        for (i in result.indices) {
            result[i] = toInetAddress(addresses[i])
        }
        return result
    }

    @JvmStatic
    fun toInetAddress(address: String?): InetAddress? {
        try {
            return InetAddress.getByName(address)
        } catch (e: UnknownHostException) {
            throw IllegalArgumentException(e)
        }
    }

    fun toInetAddress(raw: ByteArray): InetAddress {
        try {
            return InetAddress.getByAddress(raw)
        } catch (e: UnknownHostException) {
            throw IllegalArgumentException(e)
        }
    }

    fun toCIDR(cidr: String?): CIDR {
        try {
            return CIDR.parse(cidr)
        } catch (e: InvalidCIDRException) {
            throw IllegalArgumentException(e)
        }
    }

    fun toCIDRs(vararg cidrs: String?): Array<CIDR?> {
        val result: Array<CIDR?> = kotlin.arrayOfNulls<CIDR>(cidrs.size)
        for (i in result.indices) {
            result[i] = toCIDR(cidrs[i])
        }
        return result
    }

    val localhostIPv4: Inet4Address
        get() {
            val localhost = byteArrayOf(127, 0, 0, 1)
            return toInetAddress(localhost) as Inet4Address
        }
}