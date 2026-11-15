package com.expensetracker;

import java.util.List;
import java.util.OptionalDouble;

/**
 * The headline numbers for a month: what was spent, how that compares with the month
 * before, and how many budgets are on track. Pure data + arithmetic, so it is tested
 * without JavaFX; {@link SummaryStrip} only displays it.
 */
public record MonthSummary(double spent, double previousSpent, int budgetsOnTrack, int budgetsNearLimit,
                           int budgetsOver) {

    public static MonthSummary of(double spent, double previousSpent, List<BudgetSummary> budgets) {
        int onTrack = 0;
        int near = 0;
        int over = 0;
        for (BudgetSummary budget : budgets) {
            switch (budget.status()) {
                case EXCEEDED -> over++;
                case WARNING -> near++;
                default -> onTrack++;
            }
        }
        return new MonthSummary(spent, previousSpent, onTrack, near, over);
    }

    public int budgetCount() {
        return budgetsOnTrack + budgetsNearLimit + budgetsOver;
    }

    /** Change vs the previous month, e.g. 0.12 for +12%; empty when nothing was spent last month. */
    public OptionalDouble changeFromPrevious() {
        return previousSpent > 0 ? OptionalDouble.of((spent - previousSpent) / previousSpent) : OptionalDouble.empty();
    }
}
