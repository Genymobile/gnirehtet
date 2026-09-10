/*
 * Copyright (C) 2018 Genymobile
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

class CIDR : Parcelable {
    companion object {
        @Throws(InvalidCIDRException::class)
        fun parse(cidr: String?): CIDR {
            val slashIndex = cidr?.indexOf('/') ?: -1
            val address: InetAddress?
            val prefix: Int
            try {
                if (slashIndex != -1) {
                    address = Net.toInetAddress(cidr?.substring(0, slashIndex))
                    prefix = cidr?.substring(slashIndex + 1)?.toInt() ?: 32
                } else {
                    address = Net.toInetAddress(cidr)
                    prefix = 32
                }
                return CIDR(address, prefix)
            } catch (e: Exception) {
                DLog.exception("Error", e.message.toString(), e)
                throw InvalidCIDRException(cidr, e)
            }
        }

        @JvmField
        val CREATOR: Parcelable.Creator<CIDR?> = object : Parcelable.Creator<CIDR?> {
            override fun createFromParcel(source: Parcel): CIDR {
                return CIDR(source)
            }

            override fun newArray(size: Int): Array<CIDR?> {
                return arrayOfNulls<CIDR>(size)
            }
        }
    }

    val address: InetAddress?
    val prefixLength: Int

    constructor(address: InetAddress?, prefixLength: Int) {
        this.address = address
        this.prefixLength = prefixLength
    }

    private constructor(source: Parcel) {
        try {
            address = InetAddress.getByAddress(source.createByteArray())
        } catch (e: UnknownHostException) {
            throw AssertionError("Invalid address", e)
        }
        prefixLength = source.readInt()
    }

    override fun toString(): String {
        return address?.hostAddress + "/" + prefixLength
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeByteArray(address!!.getAddress())
        dest.writeInt(prefixLength)
    }
}