package com.expensetracker.suggest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CachingCategorySuggesterTest {

    private static final List<String> CATEGORIES = List.of("Food", "Transport");
    private final AtomicInteger calls = new AtomicInteger();

    private CategorySuggester counting(Optional<String> answer) {
        return (description, categories) -> {
            calls.incrementAndGet();
            return answer;
        };
    }

    @Test
    void sameDescription_isOnlyAskedOnce_ignoringCaseAndSpaces() {
        CachingCategorySuggester cache = new CachingCategorySuggester(counting(Optional.of("Food")), 10);
        cache.suggest("Lunch", CATEGORIES);
        cache.suggest("  lunch ", CATEGORIES);
        assertEquals(1, calls.get());
    }

    @Test
    void changedCategories_invalidateOldAnswers() {
        CachingCategorySuggester cache = new CachingCategorySuggester(counting(Optional.of("Food")), 10);
        cache.suggest("lunch", CATEGORIES);
        cache.suggest("lunch", List.of("Food", "Transport", "Health"));
        assertEquals(2, calls.get());
    }

    @Test
    void noAnswer_isNotCached() {
        CachingCategorySuggester cache = new CachingCategorySuggester(counting(Optional.empty()), 10);
        cache.suggest("lunch", CATEGORIES);
        cache.suggest("lunch", CATEGORIES);
        assertEquals(2, calls.get());
    }

    @Test
    void evictsLeastRecentlyUsed_whenFull() {
        CachingCategorySuggester cache = new CachingCategorySuggester(counting(Optional.of("Food")), 2);
        cache.suggest("a", CATEGORIES);
        cache.suggest("b", CATEGORIES);
        cache.suggest("a", CATEGORIES);   // touch "a": now "b" is least recently used
        cache.suggest("c", CATEGORIES);   // evicts "b"
        assertEquals(2, cache.size());

        calls.set(0);
        cache.suggest("a", CATEGORIES);   // still cached
        cache.suggest("b", CATEGORIES);   // was evicted: asks again
        assertEquals(1, calls.get());
    }
}
