package com.expensetracker;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;

/** Controller for the main window: expense table, pie chart and budget panel. */
public class MainViewController {

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
    @FXML
    private PieChart chart;
    @FXML
    private VBox budgetBox;

    private final ExpenseRepository expenses;
    private final BudgetRepository budgets;
    private final Callback<Class<?>, Object> controllerFactory;

    /**
     * @param controllerFactory builds controllers for dialogs, so they get their dependencies too
     */
    public MainViewController(ExpenseRepository expenses, BudgetRepository budgets,
                              Callback<Class<?>, Object> controllerFactory) {
        this.expenses = expenses;
        this.budgets = budgets;
        this.controllerFactory = controllerFactory;
    }

    @FXML
    private void initialize() {
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        amountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        refreshAll();
    }

    // Keeps table, chart and budget panel consistent after any change
    private void refreshAll() {
        loadExpenseData();
        loadChartData();
        refreshBudgets();
    }

    // ------------------------------------------------------------- expenses

    @FXML
    private void handleAddExpense() {
        openExpenseDialog(null);
    }

    @FXML
    private void handleEditExpense() {
        Expense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Alerts.warning("No Selection", "No Expense Selected", "Please select an expense in the table to edit.");
            return;
        }
        openExpenseDialog(selected);
    }

    // One method for both Add and Edit (they previously duplicated ~25 lines each)
    private void openExpenseDialog(Expense toEdit) {
        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("AddExpenseView.fxml"));
            loader.setControllerFactory(controllerFactory);
            Parent page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle(toEdit == null ? "Add New Expense" : "Edit Expense");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(expenseTable.getScene().getWindow());
            dialogStage.setScene(new Scene(page));

            AddExpenseController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            if (toEdit != null) {
                controller.loadExpenseData(toEdit);
            }

            dialogStage.showAndWait();
            refreshAll();
        } catch (IOException e) {
            Alerts.error("Could not open the expense form", e.getMessage());
        }
    }

    @FXML
    private void handleDeleteExpense() {
        Expense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Alerts.warning("No Selection", "No Expense Selected", "Please select an expense in the table to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Expense");
        confirm.setHeaderText("Are you sure you want to delete this expense?");
        confirm.setContentText(String.format("%s: %s - %.2f",
                selected.getDate(), selected.getCategory(), selected.getAmount()));

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                expenses.deleteExpense(selected.getId());
                refreshAll();
            }
        });
    }

    private void loadExpenseData() {
        expenseTable.setItems(FXCollections.observableArrayList(expenses.getAllExpenses()));
    }

    // ---------------------------------------------------------------- chart

    // Fills the native JavaFX pie chart with this month's totals per category
    private void loadChartData() {
        Map<String, Double> totals = expenses.getMonthlyCategorySummary();

        ObservableList<PieChart.Data> slices = FXCollections.observableArrayList();
        totals.forEach((category, total) ->
                slices.add(new PieChart.Data(String.format("%s (%.0f)", category, total), total)));

        chart.setData(slices);
        chart.setTitle(totals.isEmpty() ? "No expenses this month" : "Monthly Expenses by Category");
    }

    // -------------------------------------------------------------- budgets

    // Lets the user pick a category and set (or change) its monthly budget
    @FXML
    private void handleSetBudget() {
        List<String> categories = expenses.getAllCategoryNames();
        if (categories.isEmpty()) {
            return;
        }

        ChoiceDialog<String> categoryDialog = new ChoiceDialog<>(categories.get(0), categories);
        categoryDialog.setTitle("Set Budget");
        categoryDialog.setHeaderText("Choose a category");
        categoryDialog.setContentText("Category:");
        Optional<String> category = categoryDialog.showAndWait();
        if (category.isEmpty()) {
            return; // user cancelled
        }

        OptionalDouble current = budgets.getBudget(category.get());
        TextInputDialog amountDialog = new TextInputDialog(
                current.isPresent() ? String.format("%.2f", current.getAsDouble()) : "");
        amountDialog.setTitle("Set Budget");
        amountDialog.setHeaderText("Monthly budget for " + category.get());
        amountDialog.setContentText("Amount:");
        Optional<String> input = amountDialog.showAndWait();
        if (input.isEmpty()) {
            return; // user cancelled
        }

        double limit;
        try {
            limit = Double.parseDouble(input.get().trim());
        } catch (NumberFormatException e) {
            limit = -1;
        }
        if (limit <= 0) {
            Alerts.error("Invalid amount", "Please enter a number greater than 0.");
            return;
        }

        budgets.setBudget(category.get(), limit);
        refreshBudgets();
    }

    // Rebuilds the "Budgets This Month" panel: one label + progress bar per budgeted category
    private void refreshBudgets() {
        budgetBox.getChildren().clear();
        List<BudgetSummary> summaries = budgets.getBudgetSummaries();

        if (summaries.isEmpty()) {
            budgetBox.getChildren().add(new Label("No budgets set. Click \"Set Budget\" to add one."));
            return;
        }

        for (BudgetSummary summary : summaries) {
            Label label = new Label(String.format("%s: %.2f / %.2f (%.0f%%)",
                    summary.category(), summary.spent(), summary.monthlyLimit(), summary.usedRatio() * 100));

            ProgressBar bar = new ProgressBar(Math.min(summary.usedRatio(), 1.0));
            bar.setMaxWidth(Double.MAX_VALUE);
            bar.setStyle("-fx-accent: " + colorFor(summary.status()) + ";");

            budgetBox.getChildren().add(new VBox(2, label, bar));
        }
    }

    private static String colorFor(BudgetService.BudgetStatus status) {
        switch (status) {
            case EXCEEDED: return "#d9534f"; // red
            case WARNING:  return "#f0ad4e"; // amber
            default:       return "#5cb85c"; // green
        }
    }

    // --------------------------------------------------------------- export

    @FXML
    private void handleExportToCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Expenses as CSV");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv"));

        File file = fileChooser.showSaveDialog(expenseTable.getScene().getWindow());
        if (file == null) {
            return; // user cancelled
        }

        try (Writer writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            CsvExporter.write(expenses.getAllExpenses(), writer);
            Alerts.info("Export Successful", "Expenses successfully exported to " + file.getName());
        } catch (IOException e) {
            Alerts.error("Could not save file", "An error occurred while exporting the data: " + e.getMessage());
        }
    }
}
