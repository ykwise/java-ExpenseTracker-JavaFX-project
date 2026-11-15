package com.expensetracker.suggest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KeywordCategorySuggesterTest {

    private static final List<String> CATEGORIES = List.of("Entertainment", "Food", "Other", "Transport", "Utilities");
    private final KeywordCategorySuggester suggester = new KeywordCategorySuggester();

    @ParameterizedTest
    @CsvSource({
            "Uber to town, Transport",
            "MATATU fare, Transport",
            "Weekly groceries, Food",
            "KPLC electricity token, Utilities",
            "Netflix subscription, Entertainment"
    })
    void recognisesCommonDescriptions(String description, String expected) {
        assertEquals(Optional.of(expected), suggester.suggest(description, CATEGORIES));
    }

    @Test
    void matchesWholeWordsOnly() {
        // "business" contains "bus" but is not about transport
        assertEquals(Optional.empty(), suggester.suggest("business cards", CATEGORIES));
    }

    @Test
    void unknownDescription_hasNoSuggestion() {
        assertEquals(Optional.empty(), suggester.suggest("birthday present", CATEGORIES));
    }

    @Test
    void neverSuggestsACategoryThatDoesNotExist() {
        assertEquals(Optional.empty(), suggester.suggest("uber", List.of("Food", "Other")));
    }

    @Test
    void blankDescription_hasNoSuggestion() {
        assertEquals(Optional.empty(), suggester.suggest("  ", CATEGORIES));
    }
}
