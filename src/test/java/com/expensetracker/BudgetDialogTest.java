package com.expensetracker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BudgetDialogTest {

    @Test
    void parseAmount_acceptsThousandsSeparatorsAndSpaces() {
        assertEquals(5000.5, BudgetDialog.parseAmount(" 5,000.50 "), 1e-9);
        assertEquals(1000, BudgetDialog.parseAmount("1000"), 1e-9);
    }

    @Test
    void parseAmount_returnsMinusOneForGarbage() {
        assertEquals(-1, BudgetDialog.parseAmount("five thousand"), 1e-9);
        assertEquals(-1, BudgetDialog.parseAmount(""), 1e-9);
    }
}
