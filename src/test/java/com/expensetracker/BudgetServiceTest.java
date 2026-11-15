package com.expensetracker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.expensetracker.BudgetService.BudgetStatus;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class BudgetServiceTest {

    private static final OptionalDouble BUDGET_1000 = OptionalDouble.of(1000);

    @Test
    void noBudgetSet_returnsNoBudget() {
        assertEquals(BudgetStatus.NO_BUDGET, BudgetService.evaluate(500, OptionalDouble.empty()));
    }

    @Test
    void underEightyPercent_returnsOk() {
        assertEquals(BudgetStatus.OK, BudgetService.evaluate(799.99, BUDGET_1000));
    }

    @Test
    void exactlyEightyPercent_returnsWarning() {
        assertEquals(BudgetStatus.WARNING, BudgetService.evaluate(800, BUDGET_1000));
    }

    @Test
    void exactlyAtBudget_returnsWarningNotExceeded() {
        assertEquals(BudgetStatus.WARNING, BudgetService.evaluate(1000, BUDGET_1000));
    }

    @Test
    void overBudget_returnsExceeded() {
        assertEquals(BudgetStatus.EXCEEDED, BudgetService.evaluate(1000.01, BUDGET_1000));
    }
}
