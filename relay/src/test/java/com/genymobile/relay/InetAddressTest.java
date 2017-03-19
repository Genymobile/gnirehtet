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

import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;

public class InetAddressTest {

    @Test
    public void testIntToInetAddress() {
        int ip = 0x01020304;
        InetAddress addr = Net.toInetAddress(ip);
        Assert.assertEquals("1.2.3.4", addr.getHostAddress());
    }

    @Test
    public void testUnsignedIntToInetAddress() {
        int ip = 0xff020304;
        InetAddress addr = Net.toInetAddress(ip);
        Assert.assertEquals("255.2.3.4", addr.getHostAddress());
    }
}
