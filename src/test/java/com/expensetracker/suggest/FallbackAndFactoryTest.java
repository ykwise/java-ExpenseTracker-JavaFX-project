package com.expensetracker.suggest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FallbackAndFactoryTest {

    private static final List<String> CATEGORIES = List.of("Food", "Transport");

    @Test
    void usesPrimaryWhenItAnswers() {
        CategorySuggester s = new FallbackCategorySuggester((d, c) -> Optional.of("Food"), (d, c) -> Optional.of("Transport"));
        assertEquals(Optional.of("Food"), s.suggest("x", CATEGORIES));
    }

    @Test
    void usesFallbackWhenPrimaryHasNoAnswer() {
        CategorySuggester s = new FallbackCategorySuggester((d, c) -> Optional.empty(), (d, c) -> Optional.of("Transport"));
        assertEquals(Optional.of("Transport"), s.suggest("x", CATEGORIES));
    }

    @Test
    void withoutApiKey_appWorksOfflineWithKeywords() {
        CategorySuggester s = Suggesters.create(Map.of());
        assertTrue(s instanceof KeywordCategorySuggester);
        assertFalse(Suggesters.aiEnabled(Map.of("ANTHROPIC_API_KEY", " ")));
        assertEquals(Optional.of("Transport"), s.suggest("uber home", CATEGORIES));
    }

    @Test
    void withApiKey_buildsCachedAiChain() {
        assertTrue(Suggesters.create(Map.of("ANTHROPIC_API_KEY", "k")) instanceof CachingCategorySuggester);
        assertTrue(Suggesters.aiEnabled(Map.of("ANTHROPIC_API_KEY", "k")));
    }
}
