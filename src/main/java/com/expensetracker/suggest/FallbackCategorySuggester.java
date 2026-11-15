package com.expensetracker.suggest;

import java.util.List;
import java.util.Optional;

/** Tries {@code primary} first; if it has no answer (offline, outage, unsure), asks {@code fallback}. */
public class FallbackCategorySuggester implements CategorySuggester {

    private final CategorySuggester primary;
    private final CategorySuggester fallback;

    public FallbackCategorySuggester(CategorySuggester primary, CategorySuggester fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public Optional<String> suggest(String description, List<String> categories) {
        Optional<String> answer = primary.suggest(description, categories);
        return answer.isPresent() ? answer : fallback.suggest(description, categories);
    }
}
