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

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class AdbMonitorTest {

    private static ByteBuffer toByteBuffer(String s) {
        return ByteBuffer.wrap(s.getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    public void testReadValidPacket() {
        String data = "00180123456789ABCDEF\tdevice\n";
        String result = AdbMonitor.readPacket(toByteBuffer(data));
        Assert.assertEquals("0123456789ABCDEF\tdevice\n", result);
    }


    @Test
    public void testReadValidPacketWithGarbage() {
        String data = "00180123456789ABCDEF\tdevice\ngarbage";
        String result = AdbMonitor.readPacket(toByteBuffer(data));
        Assert.assertEquals("0123456789ABCDEF\tdevice\n", result);
    }

    @Test
    public void testReadShortPacket() {
        String data = "00180123456789ABCDEF\tdevi";
        String result = AdbMonitor.readPacket(toByteBuffer(data));
        Assert.assertNull(result);
    }

    @Test
    public void testHandlePacketDevice() {
        final String[] pSerial = new String[1];
        AdbMonitor monitor = new AdbMonitor((serial) -> {
            pSerial[0] = serial;
        });
        String packet = "0123456789ABCDEF\tdevice\n";
        monitor.handlePacket(packet);
        Assert.assertEquals("0123456789ABCDEF", pSerial[0]);
    }

    @Test
    public void testHandlePacketOffline() {
        final String[] pSerial = new String[1];
        AdbMonitor monitor = new AdbMonitor((serial) -> {
            pSerial[0] = serial;
        });
        String packet = "0123456789ABCDEF\toffline\n";
        monitor.handlePacket(packet);
        Assert.assertNull(pSerial[0]);
    }
}
