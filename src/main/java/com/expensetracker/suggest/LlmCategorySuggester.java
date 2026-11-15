package com.expensetracker.suggest;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Asks Claude (Anthropic Messages API) to classify a description into one of the categories.
 *
 * Reliability, because a network call from a desktop app will sometimes fail:
 * - Timeouts on every request, so a hung connection can't stall the worker forever.
 * - Retries only for transient failures (network errors, 429 rate limited, 5xx, 529
 *   overloaded), with exponential backoff + jitter, honouring Retry-After when sent.
 *   Client errors like 400/401 are not retried: repeating them can't succeed.
 * - A circuit breaker so a dead service is skipped instantly instead of retried per keystroke.
 * - The model's answer is only accepted if it exactly matches a known category, so a
 *   chatty or hallucinated reply can never put an invalid category into the database.
 *
 * Safety of retrying: the request only reads (classifies text) and changes nothing,
 * so it is naturally idempotent; sending it twice has no side effects.
 */
public class LlmCategorySuggester implements CategorySuggester {

    private static final Logger LOG = Logger.getLogger(LlmCategorySuggester.class.getName());
    static final URI ENDPOINT = URI.create("https://api.anthropic.com/v1/messages");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /** Thrown internally for failures worth retrying. */
    static final class TransientFailure extends Exception {
        final Optional<Long> retryAfterSeconds;

        TransientFailure(String message, Optional<Long> retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }

    /** Sleeping is injectable so tests don't actually wait for backoff delays. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final String apiKey;
    private final String model;
    private final HttpTransport transport;
    private final RetryPolicy retryPolicy;
    private final CircuitBreaker circuitBreaker;
    private final Sleeper sleeper;

    LlmCategorySuggester(String apiKey, String model, HttpTransport transport,
                         RetryPolicy retryPolicy, CircuitBreaker circuitBreaker, Sleeper sleeper) {
        this.apiKey = apiKey;
        this.model = model;
        this.transport = transport;
        this.retryPolicy = retryPolicy;
        this.circuitBreaker = circuitBreaker;
        this.sleeper = sleeper;
    }

    @Override
    public Optional<String> suggest(String description, List<String> categories) {
        if (description == null || description.isBlank() || categories.isEmpty()) {
            return Optional.empty();
        }
        if (!circuitBreaker.allowRequest()) {
            return Optional.empty(); // service recently failing: don't even try
        }

        for (int attempt = 0; attempt < retryPolicy.maxAttempts(); attempt++) {
            try {
                String answer = callApi(description, categories);
                circuitBreaker.recordSuccess();
                return matchCategory(answer, categories);
            } catch (TransientFailure e) {
                boolean lastAttempt = attempt == retryPolicy.maxAttempts() - 1;
                LOG.log(Level.FINE, "LLM attempt {0} failed: {1}", new Object[]{attempt + 1, e.getMessage()});
                if (lastAttempt) {
                    break;
                }
                final int failedAttempt = attempt;
                Duration wait = e.retryAfterSeconds
                        .map(Duration::ofSeconds)
                        .orElseGet(() -> retryPolicy.delayFor(failedAttempt));
                if (wait.compareTo(retryPolicy.maxDelay()) > 0) {
                    break; // server wants us to wait longer than a suggestion is worth: give up now
                }
                try {
                    sleeper.sleep(wait);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); // app is shutting down: stop retrying
                    return Optional.empty();
                }
            } catch (PermanentFailure e) {
                LOG.log(Level.WARNING, "LLM request rejected: {0}", e.getMessage());
                circuitBreaker.recordFailure();
                return Optional.empty();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        circuitBreaker.recordFailure();
        return Optional.empty();
    }

    /** Thrown internally for failures that retrying cannot fix (bad key, bad request). */
    static final class PermanentFailure extends Exception {
        PermanentFailure(String message) {
            super(message);
        }
    }

    private String callApi(String description, List<String> categories)
            throws TransientFailure, PermanentFailure, InterruptedException {
        HttpTransport.Response response;
        try {
            response = transport.post(ENDPOINT, Map.of(
                    "x-api-key", apiKey,
                    "anthropic-version", "2023-06-01",
                    "content-type", "application/json"), requestBody(description, categories), REQUEST_TIMEOUT);
        } catch (IOException e) {
            throw new TransientFailure("network error: " + e.getMessage(), Optional.empty());
        }

        int status = response.status();
        if (status == 200) {
            String text = Json.firstTextField(response.body());
            if (text == null) {
                throw new PermanentFailure("unexpected response format");
            }
            return text;
        }
        if (status == 429 || status == 529 || status >= 500) {
            throw new TransientFailure("HTTP " + status, response.retryAfterSeconds());
        }
        throw new PermanentFailure("HTTP " + status);
    }

    String requestBody(String description, List<String> categories) {
        String system = "You categorise personal expenses. Reply with exactly one category name from this list "
                + "and nothing else: " + String.join(", ", categories)
                + ". If none fits, reply with: Other.";
        return "{"
                + "\"model\":" + Json.quote(model) + ","
                + "\"max_tokens\":10,"
                + "\"system\":" + Json.quote(system) + ","
                + "\"messages\":[{\"role\":\"user\",\"content\":" + Json.quote(description) + "}]"
                + "}";
    }

    // Accept the answer only if it is one of the real categories (case-insensitive, trimmed)
    static Optional<String> matchCategory(String answer, List<String> categories) {
        String cleaned = answer.trim().replaceAll("[.\"']", "");
        return categories.stream().filter(c -> c.equalsIgnoreCase(cleaned)).findFirst();
    }
}
