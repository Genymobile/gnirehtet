/*
 * Based on Gnirehtet.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.genymobile.gnirehtet;

public class AppTrafficStats {

    private final int uid;
    private final String uidLabel;
    private final String appName;
    private final long receivedBytes;
    private final long sentBytes;
    private final long currentBytesPerSecond;
    private final long averageBytesPerSecond;
    private final long currentReceivedBytesPerSecond;
    private final long currentSentBytesPerSecond;
    private final long averageReceivedBytesPerSecond;
    private final long averageSentBytesPerSecond;

    public AppTrafficStats(int uid, String appName, long receivedBytes, long sentBytes, long currentBytesPerSecond,
            long averageBytesPerSecond) {
        this(uid, String.valueOf(uid), appName, receivedBytes, sentBytes, currentBytesPerSecond, averageBytesPerSecond);
    }

    public AppTrafficStats(int uid, String uidLabel, String appName, long receivedBytes, long sentBytes,
            long currentBytesPerSecond, long averageBytesPerSecond) {
        this(uid, uidLabel, appName, receivedBytes, sentBytes, currentBytesPerSecond, averageBytesPerSecond,
                currentBytesPerSecond, 0, averageBytesPerSecond, 0);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public AppTrafficStats(int uid, String uidLabel, String appName, long receivedBytes, long sentBytes,
            long currentBytesPerSecond, long averageBytesPerSecond, long currentReceivedBytesPerSecond,
            long currentSentBytesPerSecond, long averageReceivedBytesPerSecond, long averageSentBytesPerSecond) {
        this.uid = uid;
        this.uidLabel = uidLabel;
        this.appName = appName;
        this.receivedBytes = receivedBytes;
        this.sentBytes = sentBytes;
        this.currentBytesPerSecond = currentBytesPerSecond;
        this.averageBytesPerSecond = averageBytesPerSecond;
        this.currentReceivedBytesPerSecond = currentReceivedBytesPerSecond;
        this.currentSentBytesPerSecond = currentSentBytesPerSecond;
        this.averageReceivedBytesPerSecond = averageReceivedBytesPerSecond;
        this.averageSentBytesPerSecond = averageSentBytesPerSecond;
    }

    public int getUid() {
        return uid;
    }

    public String getUidLabel() {
        return uidLabel;
    }

    public String getAppName() {
        return appName;
    }

    public long getReceivedBytes() {
        return receivedBytes;
    }

    public long getSentBytes() {
        return sentBytes;
    }

    public long getTotalBytes() {
        return receivedBytes + sentBytes;
    }

    public long getCurrentBytesPerSecond() {
        return currentBytesPerSecond;
    }

    public long getAverageBytesPerSecond() {
        return averageBytesPerSecond;
    }

    public long getCurrentReceivedBytesPerSecond() {
        return currentReceivedBytesPerSecond;
    }

    public long getCurrentSentBytesPerSecond() {
        return currentSentBytesPerSecond;
    }

    public long getAverageReceivedBytesPerSecond() {
        return averageReceivedBytesPerSecond;
    }

    public long getAverageSentBytesPerSecond() {
        return averageSentBytesPerSecond;
    }
}
