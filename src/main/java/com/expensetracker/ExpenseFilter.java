package com.expensetracker;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * What the user typed in the search box and picked in the category filter, as a predicate.
 *
 * Filtering happens in memory on the selected month's expenses rather than in SQL: one
 * month is at most a few hundred rows, so this is instant and avoids a database round
 * trip on every keystroke. If the table ever showed all-time data, this would move into
 * a WHERE clause.
 *
 * @param text     matches description or category (case-insensitive), or the amount's digits
 * @param category exact category to keep, or null for all categories
 */
public record ExpenseFilter(String text, String category) implements Predicate<Expense> {

    public static final ExpenseFilter NONE = new ExpenseFilter("", null);

    public boolean isActive() {
        return !normalisedText().isEmpty() || category != null;
    }

    @Override
    public boolean test(Expense expense) {
        if (category != null && !category.equals(expense.getCategory())) {
            return false;
        }
        String query = normalisedText();
        if (query.isEmpty()) {
            return true;
        }
        return contains(expense.getDescription(), query)
                || contains(expense.getCategory(), query)
                || amountMatches(expense.getAmount(), query);
    }

    private String normalisedText() {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    // "850" finds 850.00; "1,250" and "1250" both find 1,250.00
    private static boolean amountMatches(double amount, String query) {
        String digits = query.replace(",", "");
        if (digits.isEmpty() || !digits.chars().allMatch(c -> Character.isDigit(c) || c == '.')) {
            return false;
        }
        String plain = String.format(Locale.ROOT, "%.2f", amount);
        return plain.startsWith(digits) || plain.replaceAll("\\.00$", "").equals(digits);
    }
}
