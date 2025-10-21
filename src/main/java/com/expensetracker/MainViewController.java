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
import javafx.embed.swing.SwingNode;
import javax.swing.SwingUtilities;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.general.DefaultPieDataset;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.FileWriter;

public class MainViewController {
	
	@FXML
	private void handleExportToCSV() {
	    FileChooser fileChooser = new FileChooser();
	    fileChooser.setTitle("Save Expenses as CSV");

	    // Set extension filter
	    FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv");
	    fileChooser.getExtensionFilters().add(extFilter);

	    // Show save file dialog
	    File file = fileChooser.showSaveDialog(expenseTable.getScene().getWindow());

	    if (file != null) {
	        // Use try-with-resources to ensure the writer is closed
	        try (FileWriter writer = new FileWriter(file)) {
	            // Write the CSV header
	            writer.append("Date,Category,Amount,Description\n");

	            // Get all expenses from the database
	            List<Expense> expenses = DatabaseManager.getAllExpenses();

	            // Write each expense to the file
	            for (Expense exp : expenses) {
	                writer.append(exp.getDate().toString() + ",");
	                writer.append(exp.getCategory() + ",");
	                writer.append(String.valueOf(exp.getAmount()) + ",");
	                // Handle potential commas in the description by replacing them
	                writer.append(exp.getDescription().replace(",", ";") + "\n");
	            }

	            // Show a success message
	            Alert alert = new Alert(Alert.AlertType.INFORMATION);
	            alert.setTitle("Export Successful");
	            alert.setHeaderText(null);
	            alert.setContentText("Expenses successfully exported to " + file.getName());
	            alert.showAndWait();

	        } catch (IOException e) {
	            // Show an error message
	            Alert alert = new Alert(Alert.AlertType.ERROR);
	            alert.setTitle("Export Error");
	            alert.setHeaderText("Could not save file");
	            alert.setContentText("An error occurred while exporting the data: " + e.getMessage());
	            alert.showAndWait();
	        }
	    }
	}
	
	@FXML
	private SwingNode chartNode;
	
	
	private void loadChartData() {
	    // Get the dataset from the database
	    DefaultPieDataset dataset = DatabaseManager.getMonthlyCategorySummary();

	    // Create the pie chart
	    JFreeChart pieChart = ChartFactory.createPieChart(
	        "Monthly Expenses by Category", // Chart title
	        dataset,                       // Data
	        true,                          // Include legend
	        true,
	        false
	    );

	    // Creating panel to hold the chart
	    ChartPanel chartPanel = new ChartPanel(pieChart);

	    
	    SwingUtilities.invokeLater(() -> {
	        chartNode.setContent(chartPanel);
	    });
	}
	
	
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
	    loadChartData();
	}
	
	
	@FXML
	private void handleDeleteExpense() {
	    Expense selectedExpense = expenseTable.getSelectionModel().getSelectedItem();

	    if (selectedExpense != null) {
	        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
	        alert.setTitle("Delete Expense");
	        alert.setHeaderText("Are you sure you want to delete this expense?");
	        alert.setContentText(selectedExpense.getDate() + ": " + selectedExpense.getCategory() + " - $" + selectedExpense.getAmount());

	        alert.showAndWait().ifPresent(response -> {
	            if (response == ButtonType.OK) {
	                DatabaseManager.deleteExpense(selectedExpense.getId());
	                loadExpenseData();
	                loadChartData(); 
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
	    loadChartData();
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
        loadChartData();
    }

    // Method to load data from the database and populate the table
    private void loadExpenseData() {
        List<Expense> expenseList = DatabaseManager.getAllExpenses();
        ObservableList<Expense> observableExpenseList = FXCollections.observableArrayList(expenseList);
        expenseTable.setItems(observableExpenseList);
    }
}