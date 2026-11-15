package com.expensetracker.suggest;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Builds the suggester the app uses, depending on whether an API key is configured. */
public final class Suggesters {

    static final String DEFAULT_MODEL = "claude-haiku-5-5";

    private Suggesters() {
    }

    /**
     * With ANTHROPIC_API_KEY set: cache -> (Claude with retries + circuit breaker -> keywords).
     * Without it: keywords only, fully offline. The model can be changed with EXPENSE_AI_MODEL.
     */
    public static CategorySuggester create(Map<String, String> env) {
        CategorySuggester keywords = new KeywordCategorySuggester();
        String apiKey = env.get("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return keywords;
        }
        LlmCategorySuggester llm = new LlmCategorySuggester(
                apiKey,
                env.getOrDefault("EXPENSE_AI_MODEL", DEFAULT_MODEL),
                HttpTransport.jdk(),
                new RetryPolicy(3, Duration.ofMillis(500), Duration.ofSeconds(4), RandomGenerator.getDefault()),
                new CircuitBreaker(3, Duration.ofMinutes(1), Clock.systemUTC()),
                duration -> Thread.sleep(duration.toMillis()));
        return new CachingCategorySuggester(new FallbackCategorySuggester(llm, keywords), 200);
    }

    public static boolean aiEnabled(Map<String, String> env) {
        String apiKey = env.get("ANTHROPIC_API_KEY");
        return apiKey != null && !apiKey.isBlank();
    }
}
