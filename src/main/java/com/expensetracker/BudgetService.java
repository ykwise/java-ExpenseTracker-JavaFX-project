package com.expensetracker;

import java.util.OptionalDouble;

/**
 * Decides whether a category's monthly spending is within its budget.
 *
 * Deliberately has no database or UI code: it only does the calculation,
 * so it can be unit tested without SQLite or JavaFX.
 */
public class BudgetService {

    /** Result of checking spending against a budget. */
    public enum BudgetStatus {
        NO_BUDGET,  // no budget set for this category
        OK,         // under the warning threshold
        WARNING,    // at or above 80% of the budget, up to and including 100%
        EXCEEDED    // above 100% of the budget
    }

    static final double WARNING_THRESHOLD = 0.8;

    /**
     * @param spent        total spent in the category this month (including the new expense)
     * @param monthlyLimit the category's budget, or empty if none is set
     */
    public static BudgetStatus evaluate(double spent, OptionalDouble monthlyLimit) {
        if (monthlyLimit.isEmpty()) {
            return BudgetStatus.NO_BUDGET;
        }
        double ratio = spent / monthlyLimit.getAsDouble();
        if (ratio > 1.0) {
            return BudgetStatus.EXCEEDED;
        }
        if (ratio >= WARNING_THRESHOLD) {
            return BudgetStatus.WARNING;
        }
        return BudgetStatus.OK;
    }
}
