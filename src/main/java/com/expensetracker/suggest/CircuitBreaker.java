package com.expensetracker.suggest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Stops calling a dependency that keeps failing, then periodically checks if it has recovered.
 *
 * CLOSED    - normal; calls go through. After {@code failureThreshold} failures in a row -> OPEN.
 * OPEN      - calls are refused immediately (no network, no waiting) until {@code openDuration} passes.
 * HALF_OPEN - one trial call is let through. Success -> CLOSED, failure -> OPEN again.
 *
 * For this app it means: if the AI service is down, typing a description doesn't
 * trigger a slow, doomed request every time; the keyword fallback answers instantly.
 *
 * Synchronized because it is shared by the background worker and could be shared
 * by several in future; the state changes are tiny so contention is not a concern.
 */
public final class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;

    private State state = State.CLOSED;
    private int consecutiveFailures = 0;
    private Instant openedAt;
    private boolean trialInFlight = false;

    public CircuitBreaker(int failureThreshold, Duration openDuration, Clock clock) {
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.clock = clock;
    }

    /** Returns true if a call may proceed now. In HALF_OPEN only one trial call is allowed. */
    public synchronized boolean allowRequest() {
        if (state == State.OPEN && !clock.instant().isBefore(openedAt.plus(openDuration))) {
            state = State.HALF_OPEN;
            trialInFlight = false;
        }
        switch (state) {
            case CLOSED:
                return true;
            case HALF_OPEN:
                if (trialInFlight) {
                    return false;
                }
                trialInFlight = true;
                return true;
            default:
                return false;
        }
    }

    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
        trialInFlight = false;
        state = State.CLOSED;
    }

    public synchronized void recordFailure() {
        trialInFlight = false;
        consecutiveFailures++;
        if (state == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = State.OPEN;
            openedAt = clock.instant();
        }
    }

    public synchronized State state() {
        return state;
    }
}
