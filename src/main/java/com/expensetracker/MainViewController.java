package com.expensetracker;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import java.io.IOException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.List;
import javafx.scene.control.Alert;
import java.util.Optional;
import javafx.scene.control.ButtonType;

public class MainViewController {
	
	
	@FXML
	private void handleEditExpense() {
	    // Get the selected expense from the table
	    Expense selectedExpense = expenseTable.getSelectionModel().getSelectedItem();

	    if (selectedExpense != null) {
	        try {
	            // Load the fxml file
	            FXMLLoader loader = new FXMLLoader();
	            loader.setLocation(MainApp.class.getResource("AddExpenseView.fxml"));
	            GridPane page = (GridPane) loader.load();

	            // Create the dialog Stage
	            Stage dialogStage = new Stage();
	            dialogStage.setTitle("Edit Expense");
	            dialogStage.initModality(Modality.WINDOW_MODAL);
	            Scene scene = new Scene(page);
	            dialogStage.setScene(scene);

	            
	            // Get the controller and pass the selected expense data to it
	            AddExpenseController controller = loader.getController();
	            controller.setDialogStage(dialogStage);
	            controller.loadExpenseData(selectedExpense); // We will create this method next
	            

	            // Show the dialog and wait
	            dialogStage.showAndWait();

	            // Refresh the table after the dialog is closed
	            loadExpenseData();

	        } catch (IOException e) {
	            e.printStackTrace();
	        }
	    } else {
	        // Nothing selected
	        Alert alert = new Alert(Alert.AlertType.WARNING);
	        alert.setTitle("No Selection");
	        alert.setHeaderText("No Expense Selected");
	        alert.setContentText("Please select an expense in the table to edit.");
	        alert.showAndWait();
	    }
	}
	
	
	@FXML
	private void handleDeleteExpense() {
	    // Get the selected expense from the table
	    Expense selectedExpense = expenseTable.getSelectionModel().getSelectedItem();

	    if (selectedExpense != null) {
	        
	        // Show a confirmation dialog
	        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
	        alert.setTitle("Delete Expense");
	        alert.setHeaderText("Are you sure you want to delete this expense?");
	        alert.setContentText(selectedExpense.getDate() + ": " + selectedExpense.getCategory() + " - $" + selectedExpense.getAmount());

	        // The showAndWait() method returns an Optional<ButtonType>
	        alert.showAndWait().ifPresent(response -> {
	            if (response == ButtonType.OK) {
	                // User clicked OK, delete the expense
	                DatabaseManager.deleteExpense(selectedExpense.getId());
	                // Refresh the table
	                loadExpenseData();
	            }
	        });
	       
	    } else {
	        // Nothing selected
	        Alert alert = new Alert(Alert.AlertType.WARNING);
	        alert.setTitle("No Selection");
	        alert.setHeaderText("No Expense Selected");
	        alert.setContentText("Please select an expense in the table to delete.");
	        alert.showAndWait();
	    }
	}
	
	
	@FXML
	private void handleAddExpense() {
	    try {
	        FXMLLoader loader = new FXMLLoader();
	        loader.setLocation(MainApp.class.getResource("AddExpenseView.fxml"));
	        GridPane page = (GridPane) loader.load();

	        Stage dialogStage = new Stage();
	        dialogStage.setTitle("Add New Expense");
	        dialogStage.initModality(Modality.WINDOW_MODAL);
	        Scene scene = new Scene(page);
	        dialogStage.setScene(scene);

	        
	        AddExpenseController controller = loader.getController();
	        controller.setDialogStage(dialogStage);
	        

	        dialogStage.showAndWait();
	        loadExpenseData();

	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	}

    @FXML
    private TableView<Expense> expenseTable;

    @FXML
    private TableColumn<Expense, LocalDate> dateColumn;

    @FXML
    private TableColumn<Expense, String> categoryColumn;

    @FXML
    private TableColumn<Expense, Double> amountColumn;

    @FXML
    private TableColumn<Expense, String> descriptionColumn;

    // This method is automatically called after the fxml file has been loaded.
    @FXML
    private void initialize() {
        // Set up the columns in the table
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        amountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));

        // Load the expense data
        loadExpenseData();
    }

    // Method to load data from the database and populate the table
    private void loadExpenseData() {
        List<Expense> expenseList = DatabaseManager.getAllExpenses();
        ObservableList<Expense> observableExpenseList = FXCollections.observableArrayList(expenseList);
        expenseTable.setItems(observableExpenseList);
    }
}