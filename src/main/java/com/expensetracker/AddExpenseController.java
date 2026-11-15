package com.expensetracker;

import com.expensetracker.BudgetService.BudgetStatus;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.OptionalDouble;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class AddExpenseController {

    @FXML
    private DatePicker datePicker;
    @FXML
    private ComboBox<String> categoryComboBox;
    @FXML
    private TextField amountField;
    @FXML
    private TextField descriptionField;
    @FXML
    private Button saveButton;
    @FXML
    private Button cancelButton;

    private Stage dialogStage;
    private Integer expenseId = null; // null means "Add" mode, a value means "Edit" mode

    @FXML
    private void initialize() {
        // Load categories into the ComboBox
        categoryComboBox.getItems().addAll(DatabaseManager.getAllCategoryNames());
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    // This method is called when editing an existing expense
    public void loadExpenseData(Expense expense) {
        this.expenseId = expense.getId(); // Store the ID for the save operation
        
        // Pre-fill the form fields
        datePicker.setValue(expense.getDate());
        categoryComboBox.setValue(expense.getCategory());
        amountField.setText(String.valueOf(expense.getAmount()));
        descriptionField.setText(expense.getDescription());
    }

    @FXML
    private void handleSave() {
        if (datePicker.getValue() == null || categoryComboBox.getValue() == null || amountField.getText().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Invalid Input");
            alert.setHeaderText("Please correct invalid fields");
            alert.setContentText("Date, Category, and Amount are required.");
            alert.showAndWait();
            return;
        }

        try {
            LocalDate date = datePicker.getValue();
            String category = categoryComboBox.getValue();
            double amount = Double.parseDouble(amountField.getText());
            String description = descriptionField.getText();

            if (expenseId == null) {
                // This is a new expense
                DatabaseManager.addExpense(date, category, amount, description);
            } else {
                // This is an existing expense to update
                DatabaseManager.updateExpense(expenseId, date, category, amount, description);
            }

            showBudgetAlertIfNeeded(date, category);
            dialogStage.close();

        } catch (NumberFormatException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Invalid Input");
            alert.setHeaderText("Invalid Amount");
            alert.setContentText("Please enter a valid number for the amount.");
            alert.showAndWait();
        }
    }

    // Warns (but does not block) when this month's spending in the category nears or passes its budget
    private void showBudgetAlertIfNeeded(LocalDate date, String category) {
        if (!YearMonth.from(date).equals(YearMonth.now())) {
            return; // budgets are monthly; expenses in other months don't affect this month's budget
        }

        OptionalDouble budget = DatabaseManager.getBudget(category);
        double spent = DatabaseManager.getMonthlySpent(category);
        BudgetStatus status = BudgetService.evaluate(spent, budget);

        if (status == BudgetStatus.WARNING || status == BudgetStatus.EXCEEDED) {
            double limit = budget.getAsDouble();
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Budget Alert");
            alert.setHeaderText(status == BudgetStatus.EXCEEDED
                    ? category + " budget exceeded"
                    : category + " budget almost used up");
            alert.setContentText(String.format("Spent %.2f of %.2f this month (%.0f%%).",
                    spent, limit, spent / limit * 100));
            alert.showAndWait();
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }
}