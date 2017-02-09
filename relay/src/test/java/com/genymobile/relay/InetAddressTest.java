package com.genymobile.relay;

import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;

public class InetAddressTest {

    @Test
    public void testIntToInetAddress() {
        int ip = 0x01020304;
        InetAddress addr = Route.toInetAddress(ip);
        Assert.assertEquals("1.2.3.4", addr.getHostAddress());
    }

    @Test
    public void testUnsignedIntToInetAddress() {
        int ip = 0xff020304;
        InetAddress addr = Route.toInetAddress(ip);
        Assert.assertEquals("255.2.3.4", addr.getHostAddress());
    }
}
