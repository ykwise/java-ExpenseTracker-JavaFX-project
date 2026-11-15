package com.expensetracker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ExpenseFilterTest {

    private final Expense groceries = new Expense(1, LocalDate.of(2026, 11, 3), "Food", 1250, "Weekly groceries");
    private final Expense uber = new Expense(2, LocalDate.of(2026, 11, 4), "Transport", 850.5, "Uber to town");

    @Test
    void noFilter_keepsEverything() {
        assertTrue(ExpenseFilter.NONE.test(groceries));
        assertFalse(ExpenseFilter.NONE.isActive());
    }

    @Test
    void textMatchesDescription_caseInsensitive() {
        ExpenseFilter f = new ExpenseFilter("GROCER", null);
        assertTrue(f.test(groceries));
        assertFalse(f.test(uber));
    }

    @Test
    void textMatchesCategoryName() {
        assertTrue(new ExpenseFilter("transport", null).test(uber));
    }

    @Test
    void textMatchesAmount_withOrWithoutThousandsSeparator() {
        assertTrue(new ExpenseFilter("1250", null).test(groceries));
        assertTrue(new ExpenseFilter("1,250", null).test(groceries));
        assertTrue(new ExpenseFilter("850", null).test(uber));
        assertFalse(new ExpenseFilter("850", null).test(groceries));
    }

    @Test
    void categoryFilter_isExact() {
        ExpenseFilter food = new ExpenseFilter("", "Food");
        assertTrue(food.isActive());
        assertTrue(food.test(groceries));
        assertFalse(food.test(uber));
    }

    @Test
    void textAndCategory_mustBothMatch() {
        assertFalse(new ExpenseFilter("uber", "Food").test(uber));
        assertTrue(new ExpenseFilter("uber", "Transport").test(uber));
    }

    @Test
    void blankText_isIgnored() {
        assertTrue(new ExpenseFilter("   ", null).test(uber));
        assertFalse(new ExpenseFilter("   ", null).isActive());
    }

    @Test
    void nullDescription_doesNotCrash() {
        Expense noDescription = new Expense(3, LocalDate.of(2026, 11, 5), "Other", 10, null);
        assertFalse(new ExpenseFilter("abc", null).test(noDescription));
    }
}
