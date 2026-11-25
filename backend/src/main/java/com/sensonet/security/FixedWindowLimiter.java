package com.sensonet.security;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Limitador simple de ventana fija por clave (IP). Suficiente para una sola instancia. */
public class FixedWindowLimiter {

    private final int limit;
    private final long windowMs;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowLimiter(int limit, long windowMs, Clock clock) {
        this.limit = limit;
        this.windowMs = windowMs;
        this.clock = clock;
    }

    public boolean tryAcquire(String key) {
        long now = clock.millis();
        long windowStart = now - (now % windowMs);
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> e.getValue().start < windowStart);
        }
        Window w = windows.compute(key, (k, old) ->
                old == null || old.start != windowStart ? new Window(windowStart) : old);
        return w.count.incrementAndGet() <= limit;
    }

    private static final class Window {
        final long start;
        final AtomicInteger count = new AtomicInteger();

        Window(long start) {
            this.start = start;
        }
    }
}
