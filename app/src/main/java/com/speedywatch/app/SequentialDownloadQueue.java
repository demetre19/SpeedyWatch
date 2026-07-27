package com.speedywatch.app;

import java.util.ArrayDeque;
import java.util.Objects;

final class SequentialDownloadQueue<T> {
    private final int capacity;
    private final ArrayDeque<T> items = new ArrayDeque<>();

    SequentialDownloadQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    synchronized int offer(T item) {
        Objects.requireNonNull(item, "item");
        if (items.size() >= capacity) {
            return -1;
        }
        items.addLast(item);
        return items.size();
    }

    synchronized T poll() {
        return items.pollFirst();
    }

    synchronized int size() {
        return items.size();
    }

    synchronized void clear() {
        items.clear();
    }
}
