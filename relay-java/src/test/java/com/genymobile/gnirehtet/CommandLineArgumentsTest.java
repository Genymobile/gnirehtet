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

public class CommandLineArgumentsTest {

    private static final int PROXY_PORT = 8080;

    private static final int ACCEPT_ALL = CommandLineArguments.PARAM_SERIAL | CommandLineArguments.PARAM_DNS_SERVER
            | CommandLineArguments.PARAM_ROUTES | CommandLineArguments.PARAM_PROXY_HOST
            | CommandLineArguments.PARAM_PROXY_PORT | CommandLineArguments.PARAM_PROXY_EXCLUSION_LIST;

    @Test
    public void testNoArgs() {
        CommandLineArguments args = CommandLineArguments.parse(ACCEPT_ALL);
        Assert.assertNull(args.getSerial());
        Assert.assertNull(args.getDnsServers());
        Assert.assertNull(args.getProxyHost());
        Assert.assertEquals(0, args.getProxyPort());
    }

    @Test
    public void testSerialOnly() {
        CommandLineArguments args = CommandLineArguments.parse(ACCEPT_ALL, "myserial");
        Assert.assertEquals("myserial", args.getSerial());
        Assert.assertNull(args.getDnsServers());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidParameter() {
        CommandLineArguments.parse(ACCEPT_ALL, "myserial", "other");
    }

    @Test
    public void testDnsServersOnly() {
        CommandLineArguments args = CommandLineArguments.parse(ACCEPT_ALL, "-d", "8.8.8.8");
        Assert.assertNull(args.getSerial());
        Assert.assertEquals("8.8.8.8", args.getDnsServers());
    }

    @Test
    public void testSerialAndDnsServers() {
        CommandLineArguments args = CommandLineArguments.parse(ACCEPT_ALL, "myserial", "-d", "8.8.8.8");
        Assert.assertEquals("myserial", args.getSerial());
        Assert.assertEquals("8.8.8.8", args.getDnsServers());
    }

    @Test
    public void testDnsServersAndSerial() {
        CommandLineArguments args = CommandLineArguments.parse(ACCEPT_ALL, "-d", "8.8.8.8", "myserial");
        Assert.assertEquals("myserial", args.getSerial());
        Assert.assertEquals("8.8.8.8", args.getDnsServers());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSerialWithNoDnsServersParameter() {
        CommandLineArguments.parse(ACCEPT_ALL, "myserial", "-d");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoDnsServersParameter() {
        CommandLineArguments.parse(ACCEPT_ALL, "-d");
    }

    @Test
    public void testRoutesParameter() {
        CommandLineArguments args = CommandLineArguments.parse(ACCEPT_ALL, "-r", "1.2.3.0/24");
        Assert.assertEquals("1.2.3.0/24", args.getRoutes());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoRoutesParameter() {
        CommandLineArguments.parse(ACCEPT_ALL, "-r");
    }

    @Test
    public void testProxyHostAndPort() {
        CommandLineArguments args =
                CommandLineArguments.parse(ACCEPT_ALL, "--proxy-host", "127.0.0.1", "--proxy-port", Integer.toString(PROXY_PORT));
        Assert.assertEquals("127.0.0.1", args.getProxyHost());
        Assert.assertEquals(PROXY_PORT, args.getProxyPort());
        Assert.assertNull(args.getProxyExclusionList());
    }

    @Test
    public void testProxyExclusionList() {
        CommandLineArguments args = CommandLineArguments.parse(ACCEPT_ALL,
                "--proxy-host", "127.0.0.1",
                "--proxy-port", Integer.toString(PROXY_PORT),
                "--proxy-exclusion-list", "localhost,*.example.org");
        Assert.assertEquals("localhost,*.example.org", args.getProxyExclusionList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testProxyHostWithoutProxyPort() {
        CommandLineArguments.parse(ACCEPT_ALL, "--proxy-host", "127.0.0.1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testProxyPortWithoutProxyHost() {
        CommandLineArguments.parse(ACCEPT_ALL, "--proxy-port", Integer.toString(PROXY_PORT));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyProxyHost() {
        CommandLineArguments.parse(ACCEPT_ALL, "--proxy-host", "   ", "--proxy-port", Integer.toString(PROXY_PORT));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testProxyExclusionListWithoutProxy() {
        CommandLineArguments.parse(ACCEPT_ALL, "--proxy-exclusion-list", "localhost");
    }
}
