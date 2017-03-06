package com.genymobile.gnirehtet;

import android.os.Parcel;
import android.os.Parcelable;

public class VpnConfiguration implements Parcelable {

    private String[] dnsServers;

    public VpnConfiguration(String... dnsServers) {
        this.dnsServers = dnsServers;
    }

    private VpnConfiguration(Parcel source) {
        dnsServers = source.createStringArray();
    }

    public String[] getDnsServers() {
        return dnsServers;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringArray(dnsServers);
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
