package com.sensonet.rules;

import java.time.Duration;
import java.time.Instant;

/** Estado mutable de una regla para un nodo concreto. */
public class RuleState {

    private boolean open;
    private Instant triggerStart;
    private Instant lastTs;

    public static RuleState opened() {
        RuleState s = new RuleState();
        s.open = true;
        return s;
    }

    public boolean isOpen() {
        return open;
    }

    public Instant triggerStart() {
        return triggerStart;
    }

    Instant lastTs() {
        return lastTs;
    }

    void lastTs(Instant ts) {
        this.lastTs = ts;
    }

    void markStartIfMissing(Instant ts) {
        if (triggerStart == null) {
            triggerStart = ts;
        }
    }

    Duration sinceStart(Instant ts) {
        return Duration.between(triggerStart, ts);
    }

    void reset() {
        triggerStart = null;
    }

    void open() {
        open = true;
        triggerStart = null;
    }

    void close() {
        open = false;
        triggerStart = null;
    }
}
