package com.expensetracker.suggest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Offline, rule-based suggestions. Always available, instant and free, so it is
 * the fallback whenever the LLM is not configured, unreachable or unsure.
 */
public class KeywordCategorySuggester implements CategorySuggester {

    private static final Map<String, List<String>> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put("Transport", List.of("uber", "bolt", "taxi", "matatu", "bus", "fuel", "petrol",
                "diesel", "parking", "train", "fare", "boda"));
        KEYWORDS.put("Food", List.of("lunch", "dinner", "breakfast", "groceries", "grocery", "restaurant",
                "coffee", "tea", "supermarket", "snack", "food", "pizza", "chicken"));
        KEYWORDS.put("Utilities", List.of("electricity", "kplc", "token", "water", "internet", "wifi",
                "airtime", "data bundle", "gas", "rent", "bill"));
        KEYWORDS.put("Entertainment", List.of("movie", "cinema", "netflix", "spotify", "showmax", "concert",
                "game", "tickets", "club"));
    }

    @Override
    public Optional<String> suggest(String description, List<String> categories) {
        if (description == null || description.isBlank()) {
            return Optional.empty();
        }
        String text = description.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : KEYWORDS.entrySet()) {
            if (!categories.contains(entry.getKey())) {
                continue;
            }
            for (String keyword : entry.getValue()) {
                if (containsWord(text, keyword)) {
                    return Optional.of(entry.getKey());
                }
            }
        }
        return Optional.empty();
    }

    // Whole-word match, so "bus" matches "bus fare" but not "business lunch"
    private static boolean containsWord(String text, String keyword) {
        int from = 0;
        while (true) {
            int i = text.indexOf(keyword, from);
            if (i < 0) {
                return false;
            }
            boolean startOk = i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1));
            int end = i + keyword.length();
            boolean endOk = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (startOk && endOk) {
                return true;
            }
            from = i + 1;
        }
    }
}
