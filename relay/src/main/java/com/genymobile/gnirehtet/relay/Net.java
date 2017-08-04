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

package com.genymobile.gnirehtet.relay;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public final class Net {
    private Net() {
        // not instantiable
    }

    public static InetAddress[] toInetAddresses(String... addresses) {
        InetAddress[] result = new InetAddress[addresses.length];
        for (int i = 0; i < result.length; ++i) {
            result[i] = toInetAddress(addresses[i]);
        }
        return result;
    }

    public static InetAddress toInetAddress(String address) {
        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static InetAddress toInetAddress(byte[] raw) {
        try {
            return InetAddress.getByAddress(raw);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    public static InetAddress toInetAddress(int ipAddr) {
        byte[] ip = {
                (byte) (ipAddr >>> 24),
                (byte) ((ipAddr >> 16) & 0xff),
                (byte) ((ipAddr >> 8) & 0xff),
                (byte) (ipAddr & 0xff)
        };
        return toInetAddress(ip);
    }

    public static String toString(InetSocketAddress address) {
        return address.getAddress().getHostAddress() + ":" + address.getPort();
    }
}
