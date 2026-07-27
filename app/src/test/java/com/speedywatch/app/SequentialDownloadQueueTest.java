package com.speedywatch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class SequentialDownloadQueueTest {
    @Test
    public void poll_returnsJobsInInsertionOrder() {
        SequentialDownloadQueue<String> queue = new SequentialDownloadQueue<>(3);

        assertEquals(1, queue.offer("first"));
        assertEquals(2, queue.offer("second"));
        assertEquals("first", queue.poll());
        assertEquals("second", queue.poll());
        assertNull(queue.poll());
    }

    @Test
    public void offer_rejectsJobsBeyondBoundWithoutDroppingQueuedWork() {
        SequentialDownloadQueue<String> queue = new SequentialDownloadQueue<>(2);

        assertEquals(1, queue.offer("first"));
        assertEquals(2, queue.offer("second"));
        assertEquals(-1, queue.offer("third"));
        assertEquals("first", queue.poll());
        assertEquals("second", queue.poll());
    }
}
