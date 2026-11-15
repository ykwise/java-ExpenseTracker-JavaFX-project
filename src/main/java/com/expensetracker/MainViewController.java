package com.expensetracker;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
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
    @FXML
    private Label monthLabel;
    @FXML
    private Label budgetTitle;
    @FXML
    private Button thisMonthButton;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> categoryFilter;
    @FXML
    private Label resultLabel;

    private static final String ALL_CATEGORIES = "All categories";

    // Table pipeline: month's expenses -> filtered by search/category -> sorted by column clicks
    private final ObservableList<Expense> monthExpenses = FXCollections.observableArrayList();
    private final FilteredList<Expense> filteredExpenses = new FilteredList<>(monthExpenses);

    private static final DateTimeFormatter MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    // The month the whole window is showing; table, chart and budgets all follow it
    private YearMonth selectedMonth = YearMonth.now();

    private final ExpenseRepository expenses;
    private final BudgetRepository budgets;
    private final SettingsRepository settings;
    private final CategoryRepository categories;
    private final Callback<Class<?>, Object> controllerFactory;
    private MoneyFormat money = new MoneyFormat(SettingsRepository.DEFAULT_CURRENCY);

    /**
     * @param controllerFactory builds controllers for dialogs, so they get their dependencies too
     */
    public MainViewController(ExpenseRepository expenses, BudgetRepository budgets, CategoryRepository categories,
                              SettingsRepository settings, Callback<Class<?>, Object> controllerFactory) {
        this.expenses = expenses;
        this.budgets = budgets;
        this.categories = categories;
        this.settings = settings;
        this.controllerFactory = controllerFactory;
    }

    @FXML
    private void initialize() {
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        amountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        amountColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                setText(empty || amount == null ? null : money.format(amount));
            }
        });
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));

        SortedList<Expense> sorted = new SortedList<>(filteredExpenses);
        sorted.comparatorProperty().bind(expenseTable.comparatorProperty());
        expenseTable.setItems(sorted);

        searchField.textProperty().addListener((obs, oldText, newText) -> applyFilter());
        categoryFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilter());
        refreshAll();
    }

    // Keeps table, chart and budget panel consistent after any change
    private void refreshAll() {
        money = settings.moneyFormat();
        String title = selectedMonth.format(MONTH_TITLE);
        monthLabel.setText(title);
        budgetTitle.setText("Budgets: " + title);
        thisMonthButton.setDisable(selectedMonth.equals(YearMonth.now()));
        loadExpenseData();
        loadChartData();
        refreshBudgets();
    }

    // ---------------------------------------------------------- month navigation

    @FXML
    private void handlePreviousMonth() {
        selectedMonth = selectedMonth.minusMonths(1);
        refreshAll();
    }

    @FXML
    private void handleNextMonth() {
        selectedMonth = selectedMonth.plusMonths(1);
        refreshAll();
    }

    @FXML
    private void handleThisMonth() {
        selectedMonth = YearMonth.now();
        refreshAll();
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
        confirm.setContentText(String.format("%s: %s - %s",
                selected.getDate(), selected.getCategory(), money.format(selected.getAmount())));

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                expenses.deleteExpense(selected.getId());
                refreshAll();
            }
        });
    }

    private void loadExpenseData() {
        refreshCategoryFilter();
        monthExpenses.setAll(expenses.getExpenses(selectedMonth));
        applyFilter();
    }

    // Keeps the chosen category if it still exists (e.g. after adding an expense)
    private void refreshCategoryFilter() {
        String chosen = categoryFilter.getValue();
        List<String> items = new java.util.ArrayList<>();
        items.add(ALL_CATEGORIES);
        items.addAll(categories.getAllNames());
        categoryFilter.getItems().setAll(items);
        categoryFilter.setValue(items.contains(chosen) ? chosen : ALL_CATEGORIES);
    }

    private void applyFilter() {
        String category = categoryFilter.getValue();
        ExpenseFilter filter = new ExpenseFilter(searchField.getText(),
                category == null || ALL_CATEGORIES.equals(category) ? null : category);
        filteredExpenses.setPredicate(filter);

        double shownTotal = filteredExpenses.stream().mapToDouble(Expense::getAmount).sum();
        resultLabel.setText(filter.isActive()
                ? String.format("Showing %d of %d expenses · %s", filteredExpenses.size(), monthExpenses.size(),
                        money.format(shownTotal))
                : String.format("%d expenses · %s", monthExpenses.size(), money.format(shownTotal)));
    }

    // ---------------------------------------------------------------- chart

    // Fills the native JavaFX pie chart with the selected month's totals per category
    private void loadChartData() {
        Map<String, Double> totals = expenses.getMonthlyCategorySummary(selectedMonth);

        ObservableList<PieChart.Data> slices = FXCollections.observableArrayList();
        totals.forEach((category, total) ->
                slices.add(new PieChart.Data(category + " (" + money.format(total) + ")", total)));

        chart.setData(slices);
        chart.setTitle(totals.isEmpty() ? "No expenses in " + selectedMonth.format(MONTH_TITLE) : "Spending by category");
    }

    // -------------------------------------------------------------- budgets

    // Lets the user pick a category and set (or change) its monthly budget
    @FXML
    private void handleSetBudget() {
        List<String> categoryNames = categories.getAllNames();
        if (categoryNames.isEmpty()) {
            return;
        }

        ChoiceDialog<String> categoryDialog = new ChoiceDialog<>(categoryNames.get(0), categoryNames);
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
        List<BudgetSummary> summaries = budgets.getBudgetSummaries(selectedMonth);

        if (summaries.isEmpty()) {
            budgetBox.getChildren().add(new Label("No budgets set. Click \"Set Budget\" to add one."));
            return;
        }

        for (BudgetSummary summary : summaries) {
            Label label = new Label(String.format("%s: %s / %s (%.0f%%)", summary.category(),
                    money.format(summary.spent()), money.format(summary.monthlyLimit()), summary.usedRatio() * 100));

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

    // ----------------------------------------------------------- categories

    @FXML
    private void handleManageCategories() {
        new CategoryManagerDialog(categories).showAndWait(expenseTable.getScene().getWindow());
        refreshAll(); // renames show up in the table, filter and budgets straight away
    }

    // ------------------------------------------------------------- settings

    @FXML
    private void handleSettings() {
        List<String> choices = new java.util.ArrayList<>(SettingsRepository.COMMON_CURRENCIES);
        String current = settings.getCurrency();
        if (!choices.contains(current)) {
            choices.add(0, current);
        }
        ChoiceDialog<String> dialog = new ChoiceDialog<>(current, choices);
        dialog.setTitle("Settings");
        dialog.setHeaderText("Display currency");
        dialog.setContentText("Currency:");
        dialog.showAndWait().ifPresent(code -> {
            settings.setCurrency(code);
            refreshAll();
        });
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
