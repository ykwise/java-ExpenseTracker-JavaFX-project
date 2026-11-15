package com.expensetracker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.expensetracker.BudgetService.BudgetStatus;
import org.junit.jupiter.api.Test;

class BudgetSummaryTest {

    @Test
    void usedRatio_isSpentDividedByLimit() {
        assertEquals(0.85, new BudgetSummary("Food", 1000, 850).usedRatio(), 1e-9);
    }

    @Test
    void usedRatio_canExceedOne() {
        assertEquals(1.2, new BudgetSummary("Food", 1000, 1200).usedRatio(), 1e-9);
    }

    @Test
    void status_delegatesToBudgetService() {
        assertEquals(BudgetStatus.OK, new BudgetSummary("Food", 1000, 100).status());
        assertEquals(BudgetStatus.WARNING, new BudgetSummary("Food", 1000, 900).status());
        assertEquals(BudgetStatus.EXCEEDED, new BudgetSummary("Food", 1000, 1500).status());
    }
}
