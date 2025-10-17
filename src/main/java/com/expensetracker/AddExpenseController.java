package com.expensetracker;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.scene.control.Alert;
import java.time.LocalDate;

public class AddExpenseController {
	
	@FXML
	private void handleSave() {
	    // Basic input validation
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

	        // Save the data to the database
	        DatabaseManager.addExpense(date, category, amount, description);

	        // Close the dialog
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

    // This method is called by the MainViewController to give a reference to the stage
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }
 // In AddExpenseController.java
    @FXML
    private void initialize() {
        // Load categories into the ComboBox
        categoryComboBox.getItems().addAll(DatabaseManager.getAllCategoryNames());
    }
}