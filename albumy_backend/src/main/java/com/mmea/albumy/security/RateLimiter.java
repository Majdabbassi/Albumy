package com.mmea.albumy.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class RateLimiter {

    private static final int MAX_KEYS = 10_000;
    private static final long SWEEP_EVERY = 512;

    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private long totalRequests;

    public boolean allow(String key, int maxRequests, Duration window) {
        long now = System.currentTimeMillis();
        long cutoff = now - window.toMillis();
        Deque<Long> deque = hits.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
                deque.pollFirst();
            }
            if (deque.size() >= maxRequests) {
                return false;
            }
            deque.addLast(now);
        }
        maybeSweep();
        return true;
    }

    private void maybeSweep() {
        if (hits.size() < MAX_KEYS) {
            return;
        }
        if ((++totalRequests & (SWEEP_EVERY - 1)) != 0) {
            return;
        }
        hits.entrySet().removeIf(entry -> {
            Deque<Long> deque = entry.getValue();
            synchronized (deque) {
                return deque.isEmpty();
            }
        });
    }
}