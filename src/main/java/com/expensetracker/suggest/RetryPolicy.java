package com.expensetracker.suggest;

import java.time.Duration;
import java.util.random.RandomGenerator;

/**
 * Exponential backoff with "full jitter".
 *
 * Attempt n waits a random time between 0 and min(maxDelay, baseDelay * 2^n).
 * The exponential part backs off from a struggling server; the randomness (jitter)
 * stops many clients that failed at the same moment from all retrying in lockstep
 * and hitting the server again together (a thundering herd).
 */
public final class RetryPolicy {

    private final int maxAttempts;
    private final Duration baseDelay;
    private final Duration maxDelay;
    private final RandomGenerator random;

    public RetryPolicy(int maxAttempts, Duration baseDelay, Duration maxDelay, RandomGenerator random) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        this.maxAttempts = maxAttempts;
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
        this.random = random;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public Duration maxDelay() {
        return maxDelay;
    }

    /** Upper bound of the wait after failed attempt number {@code attempt} (0-based). */
    Duration ceilingFor(int attempt) {
        long exp = baseDelay.toMillis() << Math.min(attempt, 30); // cap the shift to avoid overflow
        return Duration.ofMillis(Math.min(maxDelay.toMillis(), exp));
    }

    /** Random wait in [0, ceiling] after failed attempt number {@code attempt} (0-based). */
    public Duration delayFor(int attempt) {
        long ceiling = ceilingFor(attempt).toMillis();
        return Duration.ofMillis(ceiling == 0 ? 0 : random.nextLong(ceiling + 1));
    }
}
