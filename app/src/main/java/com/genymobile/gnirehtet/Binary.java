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

@SuppressWarnings("checkstyle:MagicNumber")
public final class Binary {

    private static final int MAX_STRING_PACKET_SIZE = 20;

    private Binary() {
        // not instantiable
    }

    public static int unsigned(byte value) {
        return value & 0xff;
    }

    public static int unsigned(short value) {
        return value & 0xffff;
    }

    public static long unsigned(int value) {
        return value & 0xffffffffL;
    }

    public static String buildPacketString(byte[] data, int len) {
        int limit = Math.min(MAX_STRING_PACKET_SIZE, len);
        StringBuilder builder = new StringBuilder();
        builder.append('[').append(len).append(" bytes] ");
        for (int i = 0; i < limit; ++i) {
            if (i != 0) {
                String sep = i % 4 == 0 ? "  " : " ";
                builder.append(sep);
            }
            builder.append(String.format("%02X", data[i] & 0xff));
        }
        if (limit < len) {
            builder.append(" ... +").append(len - limit). append(" bytes");
        }
        return builder.toString();
    }
}
