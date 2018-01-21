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

package com.genymobile.gnirehtet;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class CIDR implements Parcelable {

    private InetAddress address;
    private int prefixLength;

    public CIDR(InetAddress address, int prefixLength) {
        this.address = address;
        this.prefixLength = prefixLength;
    }

    private CIDR(Parcel source) {
        try {
            address = InetAddress.getByAddress(source.createByteArray());
        } catch (UnknownHostException e) {
            throw new AssertionError("Invalid address", e);
        }
        prefixLength = source.readInt();
    }


    @SuppressWarnings("checkstyle:MagicNumber")
    public static CIDR parse(String cidr) throws InvalidCIDRException {
        int slashIndex = cidr.indexOf('/');
        InetAddress address;
        int prefix;
        try {
            if (slashIndex != -1) {
                address = Net.toInetAddress(cidr.substring(0, slashIndex));
                prefix = Integer.parseInt(cidr.substring(slashIndex + 1));
            } else {
                address = Net.toInetAddress(cidr);
                prefix = 32;
            }
            return new CIDR(address, prefix);
        } catch (IllegalArgumentException e) {
            Log.e("Error", e.getMessage(), e);
            throw new InvalidCIDRException(cidr, e);
        } catch (Throwable e) {
            Log.e("Error", e.getMessage(), e);
            throw e;
        }
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPrefixLength() {
        return prefixLength;
    }

    @Override
    public String toString() {
        return address.getHostAddress() + "/" + prefixLength;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByteArray(address.getAddress());
        dest.writeInt(prefixLength);
    }

    public static final Creator<CIDR> CREATOR = new Creator<CIDR>() {
        @Override
        public CIDR createFromParcel(Parcel source) {
            return new CIDR(source);
        }

        @Override
        public CIDR[] newArray(int size) {
            return new CIDR[size];
        }
    };
}
