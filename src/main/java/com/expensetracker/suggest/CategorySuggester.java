package com.expensetracker.suggest;

import java.util.List;
import java.util.Optional;

/**
 * Suggests which category an expense belongs to, from its description.
 *
 * Implementations are composed as decorators (see {@link Suggesters#create}):
 * cache -> LLM with retries + circuit breaker -> keyword fallback.
 */
public interface CategorySuggester {

    /**
     * @param description what the user typed, e.g. "uber to town"
     * @param categories  the categories the answer must be one of
     * @return one of {@code categories}, or empty if there is no confident suggestion
     */
    Optional<String> suggest(String description, List<String> categories);
}
