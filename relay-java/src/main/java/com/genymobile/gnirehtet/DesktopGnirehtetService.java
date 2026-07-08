/*
 * Based on Gnirehtet.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.genymobile.gnirehtet;

import com.genymobile.gnirehtet.relay.Relay;
import com.genymobile.gnirehtet.relay.TrafficStats;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DesktopGnirehtetService {

    private static final String PACKAGE_NAME = "com.genymobile.gnirehtet";
    private static final int RELAY_START_TIMEOUT_SECONDS = 2;
    private static final int RELAY_STOP_TIMEOUT_MILLIS = 2000;
    private static final int UID_STAT_COLUMNS = 8;
    private static final int ANDROID_UIDS_PER_USER = 100000;
    private static final int DUMPSYS_UID_GROUP = 1;
    private static final int DUMPSYS_RECEIVED_BYTES_GROUP = 1;
    private static final int DUMPSYS_SENT_BYTES_GROUP = 2;
    private static final Pattern DUMPSYS_UID_HEADER_PATTERN = Pattern.compile("\\buid=(-?\\d+)\\b.*\\btag=0x0\\b");
    private static final Pattern DUMPSYS_TRAFFIC_BUCKET_PATTERN = Pattern.compile("\\brb=(\\d+)\\b.*\\btb=(\\d+)\\b");

    private final Object lock = new Object();
    private final TrafficStats trafficStats = new TrafficStats();
    private final Set<String> activeSerials = new HashSet<>();
    private final Map<String, Map<Integer, UidTraffic>> appTrafficBaselines = new HashMap<>();
    private final Map<String, AppTrafficSample> lastAppTrafficSamples = new HashMap<>();
    private final Map<String, Long> appTrafficStartTimes = new HashMap<>();

    private Relay relay;
    private Thread relayThread;
    private int relayPort;

    public List<AdbDevice> listDevices() throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(Main.getAdbPath());
        command.add("devices");
        command.add("-l");
        String output = execForOutput(command);
        List<AdbDevice> devices = addInstallInfo(parseDevices(output));
        reconcileActiveDevices(devices);
        return devices;
    }

    public void installDevice(String serial) throws Exception {
        String apkPath = Main.getApkPath();
        if (!new File(apkPath).isFile()) {
            throw new IOException("No encuentro el APK: " + apkPath + ". Genera la APK o define GNIREHTET_APK.");
        }
        Main.cmdInstall(serial);
    }

    public void uninstallDevice(String serial) throws Exception {
        try {
            Main.cmdUninstall(serial);
        } finally {
            synchronized (lock) {
                activeSerials.remove(serial);
            }
            clearApplicationTraffic(serial);
        }
    }

    public void reinstallDevice(String serial) throws Exception {
        String apkPath = Main.getApkPath();
        if (!new File(apkPath).isFile()) {
            throw new IOException("No encuentro el APK: " + apkPath + ". Genera la APK o define GNIREHTET_APK.");
        }
        Main.cmdReinstall(serial);
        synchronized (lock) {
            activeSerials.remove(serial);
        }
        clearApplicationTraffic(serial);
    }

    public void resetTunnel(String serial, int port) throws Exception {
        Main.cmdTunnel(serial, port);
    }

    public void startRelay(int port) throws IOException, InterruptedException {
        ensureRelayStarted(port);
    }

    public void startDevice(String serial, String dnsServers, String routes, int port) throws Exception {
        try {
            resetApplicationTraffic(serial);
        } catch (IOException e) {
            clearApplicationTraffic(serial);
        }
        ensureRelayStarted(port);
        Main.cmdStart(serial, emptyToNull(dnsServers), emptyToNull(routes), port);
        synchronized (lock) {
            activeSerials.add(serial);
        }
    }

    public void stopDevice(String serial) throws Exception {
        try {
            Main.cmdStop(serial);
        } finally {
            synchronized (lock) {
                activeSerials.remove(serial);
            }
        }
    }

    public void restartDevice(String serial, String dnsServers, String routes, int port) throws Exception {
        stopDevice(serial);
        startDevice(serial, dnsServers, routes, port);
    }

    public int startReadyDevices(List<AdbDevice> devices, String dnsServers, String routes, int port) throws Exception {
        int count = 0;
        for (AdbDevice device : devices) {
            if (device.isReady() && !isDeviceActive(device.getSerial())) {
                startDevice(device.getSerial(), dnsServers, routes, port);
                ++count;
            }
        }
        return count;
    }

    public void stopAll() throws Exception {
        List<String> serials;
        synchronized (lock) {
            serials = new ArrayList<>(activeSerials);
        }
        Exception firstFailure = null;
        for (String serial : serials) {
            try {
                stopDevice(serial);
            } catch (Exception e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        stopRelay();
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    public boolean isRelayRunning() {
        synchronized (lock) {
            return relayThread != null && relayThread.isAlive();
        }
    }

    public boolean isDeviceActive(String serial) {
        synchronized (lock) {
            return activeSerials.contains(serial);
        }
    }

    public Set<String> getActiveSerials() {
        synchronized (lock) {
            return new HashSet<>(activeSerials);
        }
    }

    public TrafficStats getTrafficStats() {
        return trafficStats;
    }

    public List<AppTrafficStats> getApplicationTrafficStats(String serial) throws IOException, InterruptedException {
        if (!isDeviceActive(serial)) {
            return Collections.emptyList();
        }
        Map<Integer, UidTraffic> currentTraffic = readUidTraffic(serial);
        Map<Integer, String> packages = readUidPackages(serial);
        long now = System.currentTimeMillis();

        Map<Integer, UidTraffic> baseline;
        AppTrafficSample previousSample;
        long startTime;
        synchronized (lock) {
            baseline = appTrafficBaselines.get(serial);
            if (baseline == null) {
                appTrafficBaselines.put(serial, currentTraffic);
                lastAppTrafficSamples.put(serial, new AppTrafficSample(currentTraffic, now));
                appTrafficStartTimes.put(serial, now);
                return Collections.emptyList();
            }
            previousSample = lastAppTrafficSamples.get(serial);
            startTime = appTrafficStartTimes.get(serial);
            lastAppTrafficSamples.put(serial, new AppTrafficSample(currentTraffic, now));
        }

        long elapsedSinceStart = Math.max(1, now - startTime);
        long elapsedSinceLast = previousSample == null ? elapsedSinceStart : Math.max(1, now - previousSample.timestamp);
        Map<Integer, UidTraffic> previousTraffic = previousSample == null ? Collections.emptyMap() : previousSample.uidTraffic;
        return createApplicationTrafficStats(currentTraffic, baseline, previousTraffic, packages, elapsedSinceStart, elapsedSinceLast);
    }

    static List<AppTrafficStats> createApplicationTrafficStats(Map<Integer, UidTraffic> currentTraffic,
            Map<Integer, UidTraffic> baseline, Map<Integer, UidTraffic> previousTraffic, Map<Integer, String> packages,
            long elapsedSinceStart, long elapsedSinceLast) {
        Map<String, AppTrafficAccumulator> accumulators = new HashMap<>();
        for (Map.Entry<Integer, UidTraffic> entry : currentTraffic.entrySet()) {
            int uid = entry.getKey();
            UidTraffic current = entry.getValue();
            UidTraffic base = baseline.get(uid);
            UidTraffic previous = previousTraffic.get(uid);
            long receivedBytes = Math.max(0, current.receivedBytes - (base == null ? 0 : base.receivedBytes));
            long sentBytes = Math.max(0, current.sentBytes - (base == null ? 0 : base.sentBytes));
            long totalBytes = receivedBytes + sentBytes;
            if (totalBytes == 0) {
                continue;
            }

            long baselineReceived = base == null ? 0 : base.receivedBytes;
            long baselineSent = base == null ? 0 : base.sentBytes;
            long previousReceived = previous == null ? baselineReceived : previous.receivedBytes;
            long previousSent = previous == null ? baselineSent : previous.sentBytes;
            long currentReceivedSpeed = Math.max(0, current.receivedBytes - previousReceived) * 1000 / elapsedSinceLast;
            long currentSentSpeed = Math.max(0, current.sentBytes - previousSent) * 1000 / elapsedSinceLast;
            long currentSpeed = currentReceivedSpeed + currentSentSpeed;
            long averageReceivedSpeed = receivedBytes * 1000 / elapsedSinceStart;
            long averageSentSpeed = sentBytes * 1000 / elapsedSinceStart;
            long averageSpeed = averageReceivedSpeed + averageSentSpeed;
            String appName = getApplicationGroupName(resolveAppName(packages, uid));
            AppTrafficAccumulator accumulator = accumulators.get(appName);
            if (accumulator == null) {
                accumulator = new AppTrafficAccumulator(appName);
                accumulators.put(appName, accumulator);
            }
            accumulator.add(uid, receivedBytes, sentBytes, currentSpeed, averageSpeed, currentReceivedSpeed,
                    currentSentSpeed, averageReceivedSpeed, averageSentSpeed);
        }

        List<AppTrafficStats> stats = new ArrayList<>(accumulators.size());
        for (AppTrafficAccumulator accumulator : accumulators.values()) {
            stats.add(accumulator.toStats());
        }
        stats.sort(Comparator.comparingLong(AppTrafficStats::getTotalBytes).reversed());
        return stats;
    }

    private static String getApplicationGroupName(String appName) {
        int profileIndex = appName.lastIndexOf(" (perfil ");
        if (profileIndex > 0 && appName.endsWith(")")) {
            return appName.substring(0, profileIndex);
        }
        return appName;
    }

    static String resolveAppName(Map<Integer, String> packages, int uid) {
        String packageName = packages.get(uid);
        if (packageName != null) {
            return packageName;
        }
        int appUid = Math.floorMod(uid, ANDROID_UIDS_PER_USER);
        packageName = packages.get(appUid);
        if (packageName != null && appUid != uid) {
            return packageName + " (perfil " + (uid / ANDROID_UIDS_PER_USER) + ")";
        }
        if (uid == 0) {
            return "Sistema Android (root)";
        }
        if (uid < 10000) {
            return "Sistema Android (UID " + uid + ")";
        }
        return "Aplicacion no identificada (UID " + uid + ")";
    }

    public void resetApplicationTraffic(String serial) throws IOException, InterruptedException {
        Map<Integer, UidTraffic> currentTraffic = readUidTraffic(serial);
        long now = System.currentTimeMillis();
        synchronized (lock) {
            appTrafficBaselines.put(serial, currentTraffic);
            lastAppTrafficSamples.put(serial, new AppTrafficSample(currentTraffic, now));
            appTrafficStartTimes.put(serial, now);
        }
    }

    private void clearApplicationTraffic(String serial) {
        synchronized (lock) {
            appTrafficBaselines.remove(serial);
            lastAppTrafficSamples.remove(serial);
            appTrafficStartTimes.remove(serial);
        }
    }

    private void ensureRelayStarted(int port) throws IOException, InterruptedException {
        final CountDownLatch startedLatch = new CountDownLatch(1);
        final AtomicReference<IOException> startupError = new AtomicReference<>();
        Relay relayInstance;

        synchronized (lock) {
            if (relayThread != null && relayThread.isAlive()) {
                if (relayPort != port) {
                    throw new IOException("Deten la conexion antes de cambiar el puerto del relay.");
                }
                return;
            }

            trafficStats.reset();
            relayPort = port;
            relayInstance = new Relay(port, trafficStats, startedLatch::countDown);
            relay = relayInstance;
            relayThread = new Thread(() -> runRelay(relayInstance, startupError, startedLatch), "gnirehtet-relay-gui");
            relayThread.setDaemon(true);
            relayThread.start();
        }

        if (!startedLatch.await(RELAY_START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IOException("El relay no inicio a tiempo.");
        }
        if (startupError.get() != null) {
            throw startupError.get();
        }
    }

    private void runRelay(Relay relayInstance, AtomicReference<IOException> startupError, CountDownLatch startedLatch) {
        try {
            relayInstance.run();
        } catch (IOException e) {
            startupError.set(e);
            startedLatch.countDown();
        } finally {
            synchronized (lock) {
                if (relay == relayInstance) {
                    relay = null;
                    relayThread = null;
                    activeSerials.clear();
                }
            }
        }
    }

    private void stopRelay() throws InterruptedException {
        Relay relayInstance;
        Thread thread;
        synchronized (lock) {
            relayInstance = relay;
            thread = relayThread;
        }
        if (relayInstance != null) {
            relayInstance.stop();
        }
        if (thread != null && thread != Thread.currentThread()) {
            thread.join(RELAY_STOP_TIMEOUT_MILLIS);
        }
        synchronized (lock) {
            if (relay == relayInstance) {
                relay = null;
                relayThread = null;
                activeSerials.clear();
            }
        }
    }

    private static String emptyToNull(String value) {
        String trimmed = value == null ? null : value.trim();
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

    private static List<AdbDevice> addInstallInfo(List<AdbDevice> devices) {
        List<AdbDevice> result = new ArrayList<>(devices.size());
        for (AdbDevice device : devices) {
            if (device.isReady()) {
                result.add(device.withGnirehtetVersion(queryGnirehtetVersion(device.getSerial())));
            } else {
                result.add(device);
            }
        }
        return result;
    }

    private void reconcileActiveDevices(List<AdbDevice> devices) {
        Set<String> presentReadySerials = new HashSet<>();
        for (AdbDevice device : devices) {
            if (device.isReady()) {
                presentReadySerials.add(device.getSerial());
            }
        }

        List<String> disconnectedSerials = new ArrayList<>();
        synchronized (lock) {
            for (String serial : activeSerials) {
                if (!presentReadySerials.contains(serial)) {
                    disconnectedSerials.add(serial);
                }
            }
            activeSerials.removeAll(disconnectedSerials);
        }
        for (String serial : disconnectedSerials) {
            clearApplicationTraffic(serial);
        }
    }

    private static String queryGnirehtetVersion(String serial) {
        try {
            String output = execForOutput(createAdbCommand(serial, "shell", "dumpsys", "package", PACKAGE_NAME));
            return parseGnirehtetVersion(output);
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    private static String parseGnirehtetVersion(String output) {
        String versionName = null;
        String versionCode = null;
        for (String line : output.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("versionName=")) {
                versionName = trimmed.substring("versionName=".length());
            } else if (trimmed.startsWith("versionCode=")) {
                String value = trimmed.substring("versionCode=".length());
                int spaceIndex = value.indexOf(' ');
                versionCode = spaceIndex == -1 ? value : value.substring(0, spaceIndex);
            }
        }
        if (versionName != null && versionCode != null) {
            return versionName + " (" + versionCode + ")";
        }
        if (versionName != null) {
            return versionName;
        }
        return versionCode == null ? null : "versionCode " + versionCode;
    }

    private static Map<Integer, UidTraffic> readUidTraffic(String serial) throws IOException, InterruptedException {
        try {
            String output = execForOutput(createAdbCommand(serial, "shell", "cat", "/proc/net/xt_qtaguid/stats"));
            Map<Integer, UidTraffic> traffic = parseXtQtaguidStats(output);
            if (!traffic.isEmpty()) {
                return traffic;
            }
        } catch (IOException e) {
            // try legacy /proc/uid_stat below
        }
        Map<Integer, UidTraffic> legacyTraffic = readLegacyUidTraffic(serial);
        if (legacyTraffic.isEmpty()) {
            try {
                legacyTraffic = readDumpsysUidTraffic(serial);
            } catch (IOException e) {
                legacyTraffic = Collections.emptyMap();
            }
        }
        if (legacyTraffic.isEmpty()) {
            throw new IOException("El dispositivo no expone estadisticas de trafico por aplicacion via ADB.");
        }
        return legacyTraffic;
    }

    static Map<Integer, UidTraffic> parseXtQtaguidStats(String output) {
        Map<Integer, UidTraffic> traffic = new HashMap<>();
        for (String line : output.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("idx ")) {
                continue;
            }
            String[] tokens = trimmed.split("\\s+");
            if (tokens.length < UID_STAT_COLUMNS || "lo".equals(tokens[1])) {
                continue;
            }
            try {
                int uid = Integer.parseInt(tokens[3]);
                long receivedBytes = Long.parseLong(tokens[5]);
                long sentBytes = Long.parseLong(tokens[7]);
                addTraffic(traffic, uid, receivedBytes, sentBytes);
            } catch (NumberFormatException e) {
                // ignore malformed kernel stats rows
            }
        }
        return traffic;
    }

    private static Map<Integer, UidTraffic> readLegacyUidTraffic(String serial) throws IOException, InterruptedException {
        String script = "for d in /proc/uid_stat/*; do [ -d \"$d\" ] || continue; uid=${d##*/}; "
                + "rx=$(cat \"$d/tcp_rcv\" 2>/dev/null); tx=$(cat \"$d/tcp_snd\" 2>/dev/null); "
                + "echo \"$uid ${rx:-0} ${tx:-0}\"; done";
        String output = execForOutput(createAdbCommand(serial, "shell", script));
        Map<Integer, UidTraffic> traffic = new HashMap<>();
        for (String line : output.split("\\r?\\n")) {
            String[] tokens = line.trim().split("\\s+");
            if (tokens.length < 3) {
                continue;
            }
            try {
                addTraffic(traffic, Integer.parseInt(tokens[0]), Long.parseLong(tokens[1]), Long.parseLong(tokens[2]));
            } catch (NumberFormatException e) {
                // ignore malformed legacy rows
            }
        }
        return traffic;
    }

    private static Map<Integer, UidTraffic> readDumpsysUidTraffic(String serial) throws IOException, InterruptedException {
        String output = execForOutput(createAdbCommand(serial, "shell", "dumpsys", "netstats", "detail"));
        return parseDumpsysUidTraffic(output);
    }

    static Map<Integer, UidTraffic> parseDumpsysUidTraffic(String output) {
        Map<Integer, UidTraffic> traffic = new HashMap<>();
        boolean readingUidStats = false;
        Integer currentUid = null;
        for (String line : output.split("\\r?\\n")) {
            String trimmed = line.trim();
            if ("UID stats:".equals(trimmed)) {
                readingUidStats = true;
                currentUid = null;
                continue;
            }
            if (!readingUidStats) {
                continue;
            }
            if (trimmed.endsWith("stats:")) {
                break;
            }

            Matcher header = DUMPSYS_UID_HEADER_PATTERN.matcher(trimmed);
            if (header.find()) {
                int uid = Integer.parseInt(header.group(DUMPSYS_UID_GROUP));
                currentUid = uid < 0 ? null : uid;
                continue;
            }

            if (currentUid != null) {
                addDumpsysTrafficBucket(traffic, currentUid, trimmed);
            }
        }
        return traffic;
    }

    private static void addDumpsysTrafficBucket(Map<Integer, UidTraffic> traffic, int uid, String line) {
        Matcher bucket = DUMPSYS_TRAFFIC_BUCKET_PATTERN.matcher(line);
        if (!bucket.find()) {
            return;
        }
        try {
            long receivedBytes = Long.parseLong(bucket.group(DUMPSYS_RECEIVED_BYTES_GROUP));
            long sentBytes = Long.parseLong(bucket.group(DUMPSYS_SENT_BYTES_GROUP));
            addTraffic(traffic, uid, receivedBytes, sentBytes);
        } catch (NumberFormatException e) {
            // ignore malformed dumpsys rows
        }
    }

    private static void addTraffic(Map<Integer, UidTraffic> traffic, int uid, long receivedBytes, long sentBytes) {
        UidTraffic current = traffic.get(uid);
        if (current == null) {
            traffic.put(uid, new UidTraffic(receivedBytes, sentBytes));
        } else {
            traffic.put(uid, new UidTraffic(current.receivedBytes + receivedBytes, current.sentBytes + sentBytes));
        }
    }

    private static Map<Integer, String> readUidPackages(String serial) {
        String[][] commands = {
            {"shell", "cmd", "package", "list", "packages", "-U"},
            {"shell", "cmd", "package", "list", "packages", "--user", "current", "-U"},
            {"shell", "cmd", "package", "list", "packages", "--user", "0", "-U"},
            {"shell", "pm", "list", "packages", "-U"},
            {"shell", "pm", "list", "packages", "--user", "current", "-U"},
            {"shell", "pm", "list", "packages", "--user", "0", "-U"}
        };
        for (String[] command : commands) {
            try {
                Map<Integer, String> packages = parseUidPackages(execForOutput(createAdbCommand(serial, command)));
                if (!packages.isEmpty()) {
                    return packages;
                }
            } catch (IOException | InterruptedException ignored) {
                // try the next package-listing variant
            }
        }
        return Collections.emptyMap();
    }

    static Map<Integer, String> parseUidPackages(String output) {
        Map<Integer, String> packages = new HashMap<>();
        for (String line : output.split("\\r?\\n")) {
            String packageName = null;
            Integer uid = null;
            for (String token : line.trim().split("\\s+")) {
                if (token.startsWith("package:")) {
                    packageName = token.substring("package:".length());
                } else if (token.startsWith("uid:")) {
                    try {
                        uid = Integer.parseInt(token.substring("uid:".length()));
                    } catch (NumberFormatException e) {
                        uid = null;
                    }
                }
            }
            if (packageName != null && uid != null) {
                String current = packages.get(uid);
                packages.put(uid, current == null ? packageName : current + ", " + packageName);
            }
        }
        return packages;
    }

    private static List<String> createAdbCommand(String serial, String... adbArgs) {
        List<String> command = new ArrayList<>();
        command.add(Main.getAdbPath());
        if (serial != null) {
            command.add("-s");
            command.add(serial);
        }
        Collections.addAll(command, adbArgs);
        return command;
    }

    private static String execForOutput(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = readFully(process);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Comando fallo (" + exitCode + "): " + command + "\n" + output);
        }
        return output;
    }

    private static String readFully(Process process) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append(System.lineSeparator());
            }
        }
        return builder.toString();
    }

    private static List<AdbDevice> parseDevices(String output) {
        if (output == null || output.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<AdbDevice> devices = new ArrayList<>();
        String[] lines = output.split("\\r?\\n");
        for (String line : lines) {
            AdbDevice device = parseDeviceLine(line);
            if (device != null) {
                devices.add(device);
            }
        }
        return devices;
    }

    private static AdbDevice parseDeviceLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("List of devices") || trimmed.startsWith("*")) {
            return null;
        }

        String[] tokens = trimmed.split("\\s+");
        if (tokens.length < 2) {
            return null;
        }

        String serial = tokens[0];
        String state = tokens[1];
        String model = null;
        String product = null;
        for (int i = 2; i < tokens.length; ++i) {
            if (tokens[i].startsWith("model:")) {
                model = tokens[i].substring("model:".length());
            } else if (tokens[i].startsWith("product:")) {
                product = tokens[i].substring("product:".length());
            }
        }
        return new AdbDevice(serial, state, model, product);
    }

    static final class UidTraffic {
        final long receivedBytes;
        final long sentBytes;

        private UidTraffic(long receivedBytes, long sentBytes) {
            this.receivedBytes = receivedBytes;
            this.sentBytes = sentBytes;
        }

        long totalBytes() {
            return receivedBytes + sentBytes;
        }
    }

    private static final class AppTrafficSample {
        private final Map<Integer, UidTraffic> uidTraffic;
        private final long timestamp;

        private AppTrafficSample(Map<Integer, UidTraffic> uidTraffic, long timestamp) {
            this.uidTraffic = uidTraffic;
            this.timestamp = timestamp;
        }
    }

    private static final class AppTrafficAccumulator {
        private final String appName;
        private final Set<Integer> uids = new TreeSet<>();
        private long receivedBytes;
        private long sentBytes;
        private long currentBytesPerSecond;
        private long averageBytesPerSecond;
        private long currentReceivedBytesPerSecond;
        private long currentSentBytesPerSecond;
        private long averageReceivedBytesPerSecond;
        private long averageSentBytesPerSecond;

        private AppTrafficAccumulator(String appName) {
            this.appName = appName;
        }

        @SuppressWarnings("checkstyle:ParameterNumber")
        private void add(int uid, long receivedBytes, long sentBytes, long currentBytesPerSecond,
                long averageBytesPerSecond, long currentReceivedBytesPerSecond, long currentSentBytesPerSecond,
                long averageReceivedBytesPerSecond, long averageSentBytesPerSecond) {
            uids.add(uid);
            this.receivedBytes += receivedBytes;
            this.sentBytes += sentBytes;
            this.currentBytesPerSecond += currentBytesPerSecond;
            this.averageBytesPerSecond += averageBytesPerSecond;
            this.currentReceivedBytesPerSecond += currentReceivedBytesPerSecond;
            this.currentSentBytesPerSecond += currentSentBytesPerSecond;
            this.averageReceivedBytesPerSecond += averageReceivedBytesPerSecond;
            this.averageSentBytesPerSecond += averageSentBytesPerSecond;
        }

        private AppTrafficStats toStats() {
            int uid = uids.isEmpty() ? 0 : uids.iterator().next();
            return new AppTrafficStats(uid, formatUidLabel(), appName, receivedBytes, sentBytes, currentBytesPerSecond,
                    averageBytesPerSecond, currentReceivedBytesPerSecond, currentSentBytesPerSecond,
                    averageReceivedBytesPerSecond, averageSentBytesPerSecond);
        }

        private String formatUidLabel() {
            StringBuilder builder = new StringBuilder();
            for (Integer uid : uids) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(uid);
            }
            return builder.toString();
        }
    }
}
