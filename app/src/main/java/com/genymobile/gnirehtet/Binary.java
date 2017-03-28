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

    public static String toString(byte[] data, int len) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < len; ++i) {
            if (i % 8 == 0) {
                builder.append('\n');
            }
            builder.append(String.format("%02X ", data[i] & 0xff));
        }
        return builder.toString();
    }

}
