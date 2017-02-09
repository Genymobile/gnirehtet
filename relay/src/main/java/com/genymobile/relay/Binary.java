package com.genymobile.relay;

import java.nio.ByteBuffer;

public class Binary {

    private Binary() {
        // not instantiable
    }

    public static String toString(byte[] data) {
        if (!Relay.VERBOSE) {
            return "[length = " + data.length + "]";
        }
        StringBuilder builder = new StringBuilder();
        int i = 0;
        for (byte b : data) {
            if (i % 16 == 0)
                builder.append('\n');
            else if (i % 8 == 0)
                builder.append(' ');
            ++i;
            builder.append(String.format("%02X ", b & 0xff));
        }
        return builder.toString();
    }

    public static String toString(ByteBuffer buffer) {
        byte[] data = new byte[buffer.limit()];
        System.arraycopy(buffer.array(), 0, data, 0, data.length);
        return toString(data);
    }

    public static ByteBuffer copy(ByteBuffer buffer) {
        buffer.rewind();
        ByteBuffer result = ByteBuffer.allocate(buffer.remaining());
        result.put(buffer);
        buffer.rewind();
        result.flip();
        return result;
    }
}
