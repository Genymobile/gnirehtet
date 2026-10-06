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

package com.genymobile.gnirehtet;

import android.os.Parcel;
import android.os.Parcelable;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class VpnConfiguration implements Parcelable {

    /**
     * How the VPN tells Android which network carries its traffic. See
     * {@link GnirehtetService} for why this matters and what each mode does.
     */
    /** Wait for the VPN network to be registered, then declare it as its own underlying network (default). */
    public static final String UNDERLYING_CALLBACK = "callback";
    /** The v2.5.1 behaviour: one synchronous scan right after establish(). */
    public static final String UNDERLYING_SCAN = "scan";
    /** Declare nothing. */
    public static final String UNDERLYING_NONE = "none";

    private final InetAddress[] dnsServers;
    private final CIDR[] routes;
    private final String underlyingMode;

    public VpnConfiguration() {
        this(new InetAddress[0], new CIDR[0]);
    }

    public VpnConfiguration(InetAddress[] dnsServers, CIDR[] routes) {
        this(dnsServers, routes, UNDERLYING_CALLBACK);
    }

    public VpnConfiguration(InetAddress[] dnsServers, CIDR[] routes, String underlyingMode) {
        this.dnsServers = dnsServers;
        this.routes = routes;
        this.underlyingMode = underlyingMode;
    }

    private VpnConfiguration(Parcel source) {
        int dnsCount = source.readInt();
        dnsServers = new InetAddress[dnsCount];
        try {
            for (int i = 0; i < dnsCount; ++i) {
                dnsServers[i] = InetAddress.getByAddress(source.createByteArray());
            }
        } catch (UnknownHostException e) {
            throw new AssertionError("Invalid address", e);
        }
        routes = source.createTypedArray(CIDR.CREATOR);
        underlyingMode = source.readString();
    }

    public InetAddress[] getDnsServers() {
        return dnsServers;
    }

    public CIDR[] getRoutes() {
        return routes;
    }

    public String getUnderlyingMode() {
        return underlyingMode;
    }

    /**
     * The mode an intent asked for, or the default when it asked for none or
     * for something unknown. A typo in a shell command must not silently turn
     * the workaround off.
     */
    public static String underlyingModeOf(String requested) {
        if (UNDERLYING_SCAN.equals(requested) || UNDERLYING_NONE.equals(requested)) {
            return requested;
        }
        return UNDERLYING_CALLBACK;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(dnsServers.length);
        for (InetAddress addr : dnsServers) {
            dest.writeByteArray(addr.getAddress());
        }
        dest.writeTypedArray(routes, 0);
        dest.writeString(underlyingMode);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<VpnConfiguration> CREATOR = new Creator<VpnConfiguration>() {
        @Override
        public VpnConfiguration createFromParcel(Parcel source) {
            return new VpnConfiguration(source);
        }

        @Override
        public VpnConfiguration[] newArray(int size) {
            return new VpnConfiguration[size];
        }
    };
}
