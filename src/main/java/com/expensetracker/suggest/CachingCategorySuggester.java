package com.expensetracker.suggest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Remembers recent answers so the same description doesn't cost a second API call.
 *
 * - Bounded (LRU): keeps at most {@code maxEntries}, evicting the least recently used,
 *   so memory can't grow without limit.
 * - Invalidation: the key includes the category list, so if categories ever change,
 *   old answers (which might name a category that no longer exists) are never reused.
 * - Only confident answers are cached; "no suggestion" is retried next time, since it
 *   may have been caused by a temporary outage.
 */
public class CachingCategorySuggester implements CategorySuggester {

    private final CategorySuggester delegate;
    private final Map<String, String> cache;

    public CachingCategorySuggester(CategorySuggester delegate, int maxEntries) {
        this.delegate = delegate;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) { // access order = LRU
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > maxEntries;
            }
        };
    }

    @Override
    public Optional<String> suggest(String description, List<String> categories) {
        if (description == null || description.isBlank()) {
            return Optional.empty();
        }
        String key = categories + "|" + description.trim().toLowerCase(Locale.ROOT);
        synchronized (cache) {
            String hit = cache.get(key);
            if (hit != null) {
                return Optional.of(hit);
            }
        }
        Optional<String> result = delegate.suggest(description, categories);
        result.ifPresent(category -> {
            synchronized (cache) {
                cache.put(key, category);
            }
        });
        return result;
    }

    int size() {
        synchronized (cache) {
            return cache.size();
        }
    }
}
