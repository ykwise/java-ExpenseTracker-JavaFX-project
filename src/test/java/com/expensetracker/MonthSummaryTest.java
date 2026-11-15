package com.expensetracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

class MonthSummaryTest {

    @Test
    void changeFromPrevious_isRelativeDifference() {
        assertEquals(0.25, MonthSummary.of(1250, 1000, List.of()).changeFromPrevious().getAsDouble(), 1e-9);
        assertEquals(-0.5, MonthSummary.of(500, 1000, List.of()).changeFromPrevious().getAsDouble(), 1e-9);
    }

    @Test
    void changeFromPrevious_isEmptyWhenNothingSpentLastMonth() {
        assertFalse(MonthSummary.of(500, 0, List.of()).changeFromPrevious().isPresent());
    }

    @Test
    void countsBudgetsByStatus() {
        MonthSummary s = MonthSummary.of(0, 0, List.of(
                new BudgetSummary("Food", 1000, 100),        // ok
                new BudgetSummary("Transport", 500, 450),    // near (90%)
                new BudgetSummary("Fun", 200, 260),          // over
                new BudgetSummary("Bills", 300, 0)));        // ok
        assertEquals(2, s.budgetsOnTrack());
        assertEquals(1, s.budgetsNearLimit());
        assertEquals(1, s.budgetsOver());
        assertEquals(4, s.budgetCount());
    }
}
