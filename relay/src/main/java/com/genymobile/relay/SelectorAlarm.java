package com.genymobile.relay;

import java.nio.channels.Selector;
import java.util.Timer;
import java.util.TimerTask;

public class SelectorAlarm {

    private static final int CLEANING_INTERVAL = 60 * 1000;

    private final Selector selector;
    private final Timer timer = new Timer();
    private final TimerTask timerTask = new TimerTask() {
        @Override
        public void run() {
            tick();
        }
    };

    private boolean signaled;

    public SelectorAlarm(Selector selector) {
        this.selector = selector;
    }

    public void start() {
        timer.scheduleAtFixedRate(timerTask, UDPConnection.IDLE_TIMEOUT, CLEANING_INTERVAL);
    }

    public void stop() {
        timer.cancel();
    }

    private synchronized void tick() {
        signaled = true;
        selector.wakeup();
    }

    public synchronized boolean accept() {
        if (signaled) {
            signaled = false;
            return true;
        }
        return false;
    }
}
