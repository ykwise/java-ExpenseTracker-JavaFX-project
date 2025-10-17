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

public class MainViewController {
	
	// In MainViewController.java
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

	        // --- NEW CODE ---
	        // Give the controller access to the stage.
	        AddExpenseController controller = loader.getController();
	        controller.setDialogStage(dialogStage);
	        // --- END NEW CODE ---

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