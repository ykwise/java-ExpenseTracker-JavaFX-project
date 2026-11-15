package com.expensetracker;

import com.expensetracker.BudgetService.BudgetStatus;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.OptionalDouble;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/** Controller for the Add / Edit Expense dialog. */
public class AddExpenseController {

    @FXML
    private DatePicker datePicker;
    @FXML
    private ComboBox<String> categoryComboBox;
    @FXML
    private TextField amountField;
    @FXML
    private TextField descriptionField;

    private final ExpenseRepository repository;
    private Stage dialogStage;
    private Integer expenseId = null; // null means "Add" mode, a value means "Edit" mode

    public AddExpenseController(ExpenseRepository repository) {
        this.repository = repository;
    }

    @FXML
    private void initialize() {
        categoryComboBox.getItems().addAll(repository.getAllCategoryNames());
        datePicker.setValue(LocalDate.now()); // most expenses are entered on the day
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    /** Switches the dialog to Edit mode and pre-fills it with an existing expense. */
    public void loadExpenseData(Expense expense) {
        this.expenseId = expense.getId();
        datePicker.setValue(expense.getDate());
        categoryComboBox.setValue(expense.getCategory());
        amountField.setText(String.valueOf(expense.getAmount()));
        descriptionField.setText(expense.getDescription());
    }

    @FXML
    private void handleSave() {
        if (datePicker.getValue() == null || categoryComboBox.getValue() == null || amountField.getText().isBlank()) {
            Alerts.error("Please correct invalid fields", "Date, Category, and Amount are required.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountField.getText().trim());
        } catch (NumberFormatException e) {
            Alerts.error("Invalid Amount", "Please enter a valid number for the amount.");
            return;
        }
        if (amount <= 0) {
            Alerts.error("Invalid Amount", "The amount must be greater than 0.");
            return;
        }

        LocalDate date = datePicker.getValue();
        String category = categoryComboBox.getValue();
        String description = descriptionField.getText();

        if (expenseId == null) {
            repository.addExpense(date, category, amount, description);
        } else {
            repository.updateExpense(expenseId, date, category, amount, description);
        }

        showBudgetAlertIfNeeded(date, category);
        dialogStage.close();
    }

    // Warns (but does not block) when this month's spending in the category nears or passes its budget
    private void showBudgetAlertIfNeeded(LocalDate date, String category) {
        if (!YearMonth.from(date).equals(YearMonth.now())) {
            return; // budgets are monthly; expenses in other months don't affect this month's budget
        }

        OptionalDouble budget = repository.getBudget(category);
        double spent = repository.getMonthlySpent(category);
        BudgetStatus status = BudgetService.evaluate(spent, budget);

        if (status == BudgetStatus.WARNING || status == BudgetStatus.EXCEEDED) {
            double limit = budget.getAsDouble();
            Alerts.warning("Budget Alert",
                    status == BudgetStatus.EXCEEDED
                            ? category + " budget exceeded"
                            : category + " budget almost used up",
                    String.format("Spent %.2f of %.2f this month (%.0f%%).", spent, limit, spent / limit * 100));
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }
}
