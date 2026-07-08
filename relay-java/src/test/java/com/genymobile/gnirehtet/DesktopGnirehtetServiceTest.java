/*
 * Based on Gnirehtet.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.genymobile.gnirehtet;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DesktopGnirehtetServiceTest {

    @Test
    public void testParseXtQtaguidStats() {
        String output = "idx iface acct_tag_hex uid_tag_int cnt_set rx_bytes rx_packets tx_bytes tx_packets\n"
                + "1 lo 0x0 1000 0 999 9 999 9\n"
                + "2 wlan0 0x0 10351 0 1200 12 340 3\n"
                + "3 wlan0 0x0 10351 1 800 8 160 2\n"
                + "4 rmnet_data0 0x0 10352 0 50 1 75 1\n";

        Map<Integer, DesktopGnirehtetService.UidTraffic> traffic = DesktopGnirehtetService.parseXtQtaguidStats(output);

        Assert.assertFalse(traffic.containsKey(1000));
        assertTraffic(traffic.get(10351), 2000, 500);
        assertTraffic(traffic.get(10352), 50, 75);
    }

    @Test
    public void testParseDumpsysUidTraffic() {
        String output = "Xt stats:\n"
                + "  ident=[{type=1}] uid=-1 set=ALL tag=0x0\n"
                + "    NetworkStatsHistory: bucketDuration=3600\n"
                + "      st=1 rb=999 rp=9 tb=999 tp=9 op=0\n"
                + "UID stats:\n"
                + "  Pending bytes: 0\n"
                + "  History since boot:\n"
                + "  ident=[{type=1}] uid=10351 set=DEFAULT tag=0x0\n"
                + "    NetworkStatsHistory: bucketDuration=7200\n"
                + "      st=1 rb=1200 rp=12 tb=340 tp=3 op=0\n"
                + "      st=2 rb=800 rp=8 tb=160 tp=2 op=0\n"
                + "  ident=[{type=1}] uid=-253 set=DEFAULT tag=0x0\n"
                + "    NetworkStatsHistory: bucketDuration=7200\n"
                + "      st=3 rb=777 rp=7 tb=777 tp=7 op=0\n"
                + "  ident=[{type=0}] uid=10352 set=FOREGROUND tag=0x0\n"
                + "    NetworkStatsHistory: bucketDuration=7200\n"
                + "      st=4 rb=50 rp=1 tb=75 tp=1 op=0\n"
                + "UID tag stats:\n"
                + "  ident=[{type=1}] uid=10351 set=DEFAULT tag=0x1\n"
                + "    st=5 rb=9000 rp=9 tb=9000 tp=9 op=0\n";

        Map<Integer, DesktopGnirehtetService.UidTraffic> traffic = DesktopGnirehtetService.parseDumpsysUidTraffic(output);

        Assert.assertFalse(traffic.containsKey(-253));
        assertTraffic(traffic.get(10351), 2000, 500);
        assertTraffic(traffic.get(10352), 50, 75);
    }

    @Test
    public void testParseUidPackagesCombinesSharedUidPackages() {
        String output = "package:com.instagram.android uid:10313\n"
                + "package:com.instagram.barcelona uid:10313\n"
                + "package:com.brave.browser uid:10308\n";

        Map<Integer, String> packages = DesktopGnirehtetService.parseUidPackages(output);

        Assert.assertEquals("com.instagram.android, com.instagram.barcelona", packages.get(10313));
        Assert.assertEquals("com.brave.browser", packages.get(10308));
    }

    @Test
    public void testResolveAppNameUsesBaseUidForAndroidProfiles() {
        Map<Integer, String> packages = new HashMap<>();
        packages.put(10156, "com.instagram.android");
        packages.put(10331, "com.whatsapp");

        Assert.assertEquals("com.instagram.android (perfil 10)", DesktopGnirehtetService.resolveAppName(packages, 1010156));
        Assert.assertEquals("com.whatsapp (perfil 999)", DesktopGnirehtetService.resolveAppName(packages, 99910331));
        Assert.assertEquals("Sistema Android (UID 1051)", DesktopGnirehtetService.resolveAppName(packages, 1051));
    }

    @Test
    public void testCreateApplicationTrafficStatsCombinesProfileUids() {
        String currentOutput = "idx iface acct_tag_hex uid_tag_int cnt_set rx_bytes rx_packets tx_bytes tx_packets\n"
                + "1 wlan0 0x0 10313 0 1000 10 100 1\n"
                + "2 wlan0 0x0 1010313 0 2000 20 300 3\n";
        Map<Integer, DesktopGnirehtetService.UidTraffic> currentTraffic =
                DesktopGnirehtetService.parseXtQtaguidStats(currentOutput);
        Map<Integer, String> packages = new HashMap<>();
        packages.put(10313, "com.instagram.android");

        List<AppTrafficStats> stats = DesktopGnirehtetService.createApplicationTrafficStats(currentTraffic,
                Collections.emptyMap(), Collections.emptyMap(), packages, 1000, 1000);

        Assert.assertEquals(1, stats.size());
        AppTrafficStats instagram = stats.get(0);
        Assert.assertEquals("com.instagram.android", instagram.getAppName());
        Assert.assertEquals("10313, 1010313", instagram.getUidLabel());
        Assert.assertEquals(3000, instagram.getReceivedBytes());
        Assert.assertEquals(400, instagram.getSentBytes());
        Assert.assertEquals(3400, instagram.getTotalBytes());
        Assert.assertEquals(3400, instagram.getCurrentBytesPerSecond());
        Assert.assertEquals(3400, instagram.getAverageBytesPerSecond());
    }

    private static void assertTraffic(DesktopGnirehtetService.UidTraffic traffic, long receivedBytes, long sentBytes) {
        Assert.assertNotNull(traffic);
        Assert.assertEquals(receivedBytes, traffic.receivedBytes);
        Assert.assertEquals(sentBytes, traffic.sentBytes);
        Assert.assertEquals(receivedBytes + sentBytes, traffic.totalBytes());
    }
}
