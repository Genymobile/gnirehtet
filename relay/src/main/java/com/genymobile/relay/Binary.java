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

package com.genymobile.relay;

import java.nio.ByteBuffer;

@SuppressWarnings("checkstyle:MagicNumber")
public final class Binary {

    private Binary() {
        // not instantiable
    }

    public static String toString(byte[] data, int offset, int length) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; ++i) {
            byte b = data[offset + i];
            if (i % 16 == 0) {
                builder.append('\n');
            } else if (i % 8 == 0) {
                builder.append(' ');
            }
            ++i;
            builder.append(String.format("%02X ", b & 0xff));
        }
        return builder.toString();
    }

    public static String toString(byte[] data) {
        return toString(data, 0, data.length);
    }

    public static String toString(ByteBuffer buffer) {
        return toString(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
    }

    public static ByteBuffer copy(ByteBuffer buffer) {
        buffer.rewind();
        ByteBuffer result = ByteBuffer.allocate(buffer.remaining());
        result.put(buffer);
        buffer.rewind();
        result.flip();
        return result;
    }

    public static ByteBuffer slice(ByteBuffer buffer, int offset, int length) {
        // save
        int position = buffer.position();
        int limit = buffer.limit();

        // slice
        buffer.limit(offset + length).position(offset);
        ByteBuffer result = buffer.slice();

        // restore
        buffer.limit(limit).position(position);

        return result;
    }
}
