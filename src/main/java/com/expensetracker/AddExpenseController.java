package com.expensetracker;

import java.time.LocalDate;
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
            
            dialogStage.close();

        } catch (NumberFormatException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Invalid Input");
            alert.setHeaderText("Invalid Amount");
            alert.setContentText("Please enter a valid number for the amount.");
            alert.showAndWait();
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }
}