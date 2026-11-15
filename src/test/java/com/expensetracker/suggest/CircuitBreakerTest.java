package com.expensetracker.suggest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.expensetracker.suggest.CircuitBreaker.State;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CircuitBreakerTest {

    /** A clock the test can move forward by hand. */
    static final class ManualClock extends Clock {
        Instant now = Instant.parse("2026-11-15T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final ManualClock clock = new ManualClock();
    private final CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofMinutes(1), clock);

    @Test
    void staysClosedBelowThreshold() {
        breaker.recordFailure();
        breaker.recordFailure();
        assertEquals(State.CLOSED, breaker.state());
        assertTrue(breaker.allowRequest());
    }

    @Test
    void successResetsTheFailureCount() {
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordSuccess();
        breaker.recordFailure();
        assertEquals(State.CLOSED, breaker.state());
    }

    @Test
    void opensAfterThresholdAndRefusesCalls() {
        tripBreaker();
        assertEquals(State.OPEN, breaker.state());
        assertFalse(breaker.allowRequest());
    }

    @Test
    void afterCooldown_allowsExactlyOneTrialCall() {
        tripBreaker();
        clock.advance(Duration.ofMinutes(1));

        assertTrue(breaker.allowRequest());   // the trial
        assertEquals(State.HALF_OPEN, breaker.state());
        assertFalse(breaker.allowRequest());  // no second concurrent trial
    }

    @Test
    void successfulTrial_closesTheBreaker() {
        tripBreaker();
        clock.advance(Duration.ofMinutes(1));
        breaker.allowRequest();
        breaker.recordSuccess();
        assertEquals(State.CLOSED, breaker.state());
        assertTrue(breaker.allowRequest());
    }

    @Test
    void failedTrial_reopensImmediately() {
        tripBreaker();
        clock.advance(Duration.ofMinutes(1));
        breaker.allowRequest();
        breaker.recordFailure();
        assertEquals(State.OPEN, breaker.state());
        assertFalse(breaker.allowRequest());
    }

    private void tripBreaker() {
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordFailure();
    }
}
