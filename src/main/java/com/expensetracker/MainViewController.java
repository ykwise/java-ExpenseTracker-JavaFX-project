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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;

/**
 * Controller for the main window. It only coordinates: it tracks the selected month,
 * loads that month's data, hands it to the components ({@link ExpenseTable},
 * {@link SummaryStrip}, {@link BudgetPanel}, the chart) and opens dialogs.
 */
public class MainViewController {

    private static final DateTimeFormatter MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_NAME = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH);

    @FXML private Label monthLabel;
    @FXML private Button thisMonthButton;
    @FXML private SummaryStrip summaryStrip;
    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> categoryFilter;
    @FXML private TableView<Expense> expenseTable;
    @FXML private Label resultLabel;
    @FXML private PieChart chart;
    @FXML private Label budgetTitle;
    @FXML private BudgetPanel budgetPanel;

    private ExpenseTable table;

    // The month the whole window is showing; table, chart, summary and budgets all follow it
    private YearMonth selectedMonth = YearMonth.now();
    private MoneyFormat money = new MoneyFormat(SettingsRepository.DEFAULT_CURRENCY);

    private final ExpenseRepository expenses;
    private final BudgetRepository budgets;
    private final CategoryRepository categories;
    private final SettingsRepository settings;
    private final Callback<Class<?>, Object> controllerFactory;

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
        table = new ExpenseTable(expenseTable, searchField, categoryFilter, resultLabel,
                () -> money, this::openExpenseDialog, this::deleteExpense);

        // Edit/Delete only make sense with a row selected
        editButton.disableProperty().bind(table.noSelection());
        deleteButton.disableProperty().bind(table.noSelection());

        // Keyboard shortcuts need the scene, which exists only after the window is built
        expenseTable.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) {
                installShortcuts(scene);
            }
        });
        refreshAll();
    }

    // ⌘ on macOS, Ctrl elsewhere (SHORTCUT_DOWN picks the right one)
    private void installShortcuts(Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN),
                this::handleAddExpense);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN),
                table::focusSearch);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.LEFT, KeyCombination.SHORTCUT_DOWN),
                this::handlePreviousMonth);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.SHORTCUT_DOWN),
                this::handleNextMonth);
    }

    // Keeps every part of the window consistent after any change
    private void refreshAll() {
        money = settings.moneyFormat();
        monthLabel.setText(selectedMonth.format(MONTH_TITLE));
        thisMonthButton.setVisible(!selectedMonth.equals(YearMonth.now()));
        budgetTitle.setText("Budgets for " + selectedMonth.format(MONTH_NAME));

        Map<String, Double> totals = expenses.getMonthlyCategorySummary(selectedMonth);
        double previousTotal = sum(expenses.getMonthlyCategorySummary(selectedMonth.minusMonths(1)));
        List<BudgetSummary> budgetSummaries = budgets.getBudgetSummaries(selectedMonth);

        table.show(selectedMonth, expenses.getExpenses(selectedMonth), categories.getAllNames());
        showChart(totals);
        summaryStrip.show(selectedMonth, MonthSummary.of(sum(totals), previousTotal, budgetSummaries), money);
        budgetPanel.show(budgetSummaries, money);
    }

    private static double sum(Map<String, Double> totals) {
        return totals.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    private void showChart(Map<String, Double> totals) {
        ObservableList<PieChart.Data> slices = FXCollections.observableArrayList();
        // Labels on the slices are off; the legend carries the amounts instead
        totals.forEach((category, total) ->
                slices.add(new PieChart.Data(category + "  " + money.format(total), total)));
        chart.setData(slices);
        chart.setTitle(totals.isEmpty() ? "Nothing spent in " + selectedMonth.format(MONTH_NAME) : "Spending by category");
    }

    // ---------------------------------------------------------- month navigation

    @FXML
    private void handlePreviousMonth() {
        showMonth(selectedMonth.minusMonths(1));
    }

    @FXML
    private void handleNextMonth() {
        showMonth(selectedMonth.plusMonths(1));
    }

    @FXML
    private void handleThisMonth() {
        showMonth(YearMonth.now());
    }

    private void showMonth(YearMonth month) {
        selectedMonth = month;
        table.clearSelection();
        refreshAll();
    }

    // ------------------------------------------------------------- expenses

    @FXML
    private void handleAddExpense() {
        openExpenseDialog(null);
    }

    @FXML
    private void handleEditExpense() {
        if (table.selected() != null) {
            openExpenseDialog(table.selected());
        }
    }

    @FXML
    private void handleDeleteExpense() {
        if (table.selected() != null) {
            deleteExpense(table.selected());
        }
    }

    // One dialog for both Add and Edit
    private void openExpenseDialog(Expense toEdit) {
        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("AddExpenseView.fxml"));
            loader.setControllerFactory(controllerFactory);
            Parent page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle(toEdit == null ? "Add expense" : "Edit expense");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(expenseTable.getScene().getWindow());
            Scene scene = new Scene(page);
            Theme.apply(scene);
            dialogStage.setScene(scene);
            dialogStage.setResizable(false);

            AddExpenseController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            if (toEdit != null) {
                controller.loadExpenseData(toEdit);
            }

            dialogStage.showAndWait();
            // If the expense was saved in another month, jump there so the user sees it
            controller.savedDate()
                    .map(YearMonth::from)
                    .filter(month -> !month.equals(selectedMonth))
                    .ifPresentOrElse(this::showMonth, this::refreshAll);
        } catch (IOException e) {
            Alerts.error("Could not open the expense form", e.getMessage());
        }
    }

    private void deleteExpense(Expense expense) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Theme.apply(confirm.getDialogPane());
        confirm.initOwner(expenseTable.getScene().getWindow());
        confirm.setTitle("Delete expense");
        confirm.setHeaderText("Delete this expense?");
        String description = expense.getDescription() == null || expense.getDescription().isBlank()
                ? expense.getCategory() : expense.getDescription();
        confirm.setContentText(String.format("%s, %s on %s.", description, money.format(expense.getAmount()),
                expense.getDate().format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH))));
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.OK)).setText("Delete");

        confirm.showAndWait().filter(response -> response == ButtonType.OK).ifPresent(response -> {
            expenses.deleteExpense(expense.getId());
            refreshAll();
        });
    }

    // ------------------------------------------------------- other dialogs

    @FXML
    private void handleSetBudget() {
        if (BudgetDialog.show(categories, budgets, money)) {
            refreshAll();
        }
    }

    @FXML
    private void handleManageCategories() {
        new CategoryManagerDialog(categories).showAndWait(expenseTable.getScene().getWindow());
        refreshAll(); // renames show up in the table, filter and budgets straight away
    }

    @FXML
    private void handleSettings() {
        if (SettingsDialog.show(settings)) {
            refreshAll();
        }
    }

    @FXML
    private void handleExportToCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export expenses");
        fileChooser.setInitialFileName("expenses-" + LocalDate.now() + ".csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv"));

        File file = fileChooser.showSaveDialog(expenseTable.getScene().getWindow());
        if (file == null) {
            return; // cancelled
        }
        try (Writer writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            CsvExporter.write(expenses.getAllExpenses(), writer);
            Alerts.info("Export complete", "All expenses were exported to " + file.getName() + ".");
        } catch (IOException e) {
            Alerts.error("Export failed", "Could not write " + file.getName() + ": " + e.getMessage());
        }
    }
}
