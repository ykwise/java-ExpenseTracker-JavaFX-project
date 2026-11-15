package com.expensetracker;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TextInputDialog;

/** Asks for a category and a monthly limit, then saves the budget. */
final class BudgetDialog {

    private BudgetDialog() {
    }

    /** @return true if a budget was saved */
    static boolean show(CategoryRepository categories, BudgetRepository budgets, MoneyFormat money) {
        List<String> names = categories.getAllNames();
        if (names.isEmpty()) {
            return false;
        }

        ChoiceDialog<String> categoryDialog = new ChoiceDialog<>(names.get(0), names);
        Theme.apply(categoryDialog.getDialogPane());
        categoryDialog.setTitle("Set budget");
        categoryDialog.setHeaderText("Which category?");
        categoryDialog.setContentText("Category:");
        Optional<String> category = categoryDialog.showAndWait();
        if (category.isEmpty()) {
            return false; // cancelled
        }

        OptionalDouble current = budgets.getBudget(category.get());
        TextInputDialog amountDialog = new TextInputDialog(
                current.isPresent() ? money.number(current.getAsDouble()).replace(",", "") : "");
        Theme.apply(amountDialog.getDialogPane());
        amountDialog.setTitle("Set budget");
        amountDialog.setHeaderText("Monthly budget for " + category.get());
        amountDialog.setContentText("Amount (" + money.currencyCode() + "):");
        Optional<String> input = amountDialog.showAndWait();
        if (input.isEmpty()) {
            return false; // cancelled
        }

        double limit = parseAmount(input.get());
        if (limit <= 0) {
            Alerts.error("Budget not saved", "Enter an amount greater than 0, for example 5000.");
            return false;
        }
        budgets.setBudget(category.get(), limit);
        return true;
    }

    /** "5,000.50" -> 5000.5; anything unparseable -> -1. */
    static double parseAmount(String text) {
        try {
            return Double.parseDouble(text.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
