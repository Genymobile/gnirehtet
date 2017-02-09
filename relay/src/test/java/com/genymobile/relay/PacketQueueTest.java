package com.genymobile.relay;

import org.junit.Assert;
import org.junit.Test;

public class PacketQueueTest {

    @Test
    public void testEmptyQueue() {
        NetBuffer queue = new NetBuffer(2);
        Assert.assertTrue("Queue must be empty", queue.isEmpty());
        Assert.assertNull("No element must be retrieved from an empty queue", queue.poll());
    }

    @Test
    public void testFullQueue() {
        NetBuffer queue = new NetBuffer(2);
        Assert.assertFalse("Queue must not be full", queue.isFull());
        queue.offer(null);
        Assert.assertFalse("Queue must not be full", queue.isFull());
        queue.offer(null);
        Assert.assertTrue("Queue must be full", queue.isFull());
        Assert.assertFalse("No element must be added to a full queue", queue.offer(null));
    }
}
