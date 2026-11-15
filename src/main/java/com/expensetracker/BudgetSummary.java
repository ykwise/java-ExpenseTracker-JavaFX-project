package com.expensetracker;

import java.util.OptionalDouble;

/**
 * A category's budget alongside what has been spent in it this month.
 * Immutable value object used to render the budget overview panel.
 */
public record BudgetSummary(String category, double monthlyLimit, double spent) {

    /** Fraction of the budget used, e.g. 0.85 for 85%. Can exceed 1.0. */
    public double usedRatio() {
        return spent / monthlyLimit;
    }

    public BudgetService.BudgetStatus status() {
        return BudgetService.evaluate(spent, OptionalDouble.of(monthlyLimit));
    }
}
