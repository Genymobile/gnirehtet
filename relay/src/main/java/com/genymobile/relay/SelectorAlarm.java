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
