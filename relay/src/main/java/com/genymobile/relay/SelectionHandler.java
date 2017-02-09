package com.genymobile.relay;

import java.nio.channels.SelectionKey;

public interface SelectionHandler {

    void onReady(SelectionKey selectionKey);
}
