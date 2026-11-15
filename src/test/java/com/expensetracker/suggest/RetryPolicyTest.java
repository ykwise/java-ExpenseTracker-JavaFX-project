package com.expensetracker.suggest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private final RetryPolicy policy =
            new RetryPolicy(5, Duration.ofMillis(500), Duration.ofSeconds(4), new Random(42));

    @Test
    void ceilingDoublesEachAttempt() {
        assertEquals(Duration.ofMillis(500), policy.ceilingFor(0));
        assertEquals(Duration.ofMillis(1000), policy.ceilingFor(1));
        assertEquals(Duration.ofMillis(2000), policy.ceilingFor(2));
    }

    @Test
    void ceilingIsCappedAtMaxDelay() {
        assertEquals(Duration.ofSeconds(4), policy.ceilingFor(3));
        assertEquals(Duration.ofSeconds(4), policy.ceilingFor(40)); // no overflow on big attempts
    }

    @Test
    void jitteredDelayStaysWithinZeroAndCeiling() {
        for (int attempt = 0; attempt < 6; attempt++) {
            for (int i = 0; i < 200; i++) {
                long delay = policy.delayFor(attempt).toMillis();
                assertTrue(delay >= 0 && delay <= policy.ceilingFor(attempt).toMillis());
            }
        }
    }

    @Test
    void jitterActuallySpreadsDelays() {
        long distinct = java.util.stream.IntStream.range(0, 50)
                .mapToLong(i -> policy.delayFor(2).toMillis()).distinct().count();
        assertTrue(distinct > 10, "delays should vary, got " + distinct + " distinct values");
    }

    @Test
    void rejectsZeroAttempts() {
        assertThrows(IllegalArgumentException.class,
                () -> new RetryPolicy(0, Duration.ofMillis(1), Duration.ofMillis(1), new Random()));
    }
}
