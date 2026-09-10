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

import android.os.Parcel
import android.os.Parcelable
import java.net.InetAddress
import java.net.UnknownHostException

class VpnConfiguration : Parcelable {
    val dnsServers: Array<InetAddress?>
    val routes: Array<CIDR?>?

    constructor() {
        this.dnsServers = arrayOfNulls<InetAddress>(0)
        this.routes = kotlin.arrayOfNulls<CIDR>(0)
    }

    constructor(dnsServers: Array<InetAddress?>, routes: Array<CIDR?>?) {
        this.dnsServers = dnsServers
        this.routes = routes
    }

    private constructor(source: Parcel) {
        val dnsCount = source.readInt()
        dnsServers = arrayOfNulls<InetAddress>(dnsCount)
        try {
            for (i in 0..<dnsCount) {
                dnsServers[i] = InetAddress.getByAddress(source.createByteArray())
            }
        } catch (e: UnknownHostException) {
            throw AssertionError("Invalid address", e)
        }
        routes = source.createTypedArray<CIDR?>(CIDR.CREATOR)
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(dnsServers.size)
        for (addr in dnsServers) {
            dest.writeByteArray(addr?.getAddress())
        }
        dest.writeTypedArray(routes, 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<VpnConfiguration?> =
            object : Parcelable.Creator<VpnConfiguration?> {
                override fun createFromParcel(source: Parcel): VpnConfiguration {
                    return VpnConfiguration(source)
                }

                override fun newArray(size: Int): Array<VpnConfiguration?> {
                    return arrayOfNulls<VpnConfiguration>(size)
                }
            }
    }
}