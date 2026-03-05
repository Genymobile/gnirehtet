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

    private final InetAddress[] dnsServers;
    private final CIDR[] routes;
    private final String proxyHost;
    private final int proxyPort;
    private final String[] exclusionList;

    public VpnConfiguration() {
        this.dnsServers = new InetAddress[0];
        this.routes = new CIDR[0];
        this.proxyHost = null;
        this.proxyPort = -1;
        this.exclusionList = new String[0];
    }

    public VpnConfiguration(InetAddress[] dnsServers, CIDR[] routes) {
        this(dnsServers, routes, null, -1, new String[0]);
    }

    public VpnConfiguration(InetAddress[] dnsServers, CIDR[] routes, String proxyHost, int proxyPort, String[] exclusionList) {
        this.dnsServers = dnsServers;
        this.routes = routes;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.exclusionList = exclusionList;
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
        proxyHost = source.readString();
        proxyPort = source.readInt();
        exclusionList = source.createStringArray();
    }

    public InetAddress[] getDnsServers() {
        return dnsServers;
    }

    public CIDR[] getRoutes() {
        return routes;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public String[] getExclusionList() {
        return exclusionList;
    }

    public boolean hasProxy() {
        return proxyHost != null && proxyPort > 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(dnsServers.length);
        for (InetAddress addr : dnsServers) {
            dest.writeByteArray(addr.getAddress());
        }
        dest.writeTypedArray(routes, 0);
        dest.writeString(proxyHost);
        dest.writeInt(proxyPort);
        dest.writeStringArray(exclusionList);
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
