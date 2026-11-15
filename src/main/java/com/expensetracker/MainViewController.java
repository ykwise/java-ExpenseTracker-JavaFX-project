package com.expensetracker;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;

/**
 * Controller for the main window. It coordinates: it loads data for the selected month
 * and hands it to the table, the chart and the {@link SummaryStrip} / {@link BudgetPanel}
 * components, and opens dialogs. Display logic for those components lives in the components.
 */
public class MainViewController {

    private static final DateTimeFormatter MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_NAME = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH);
    private static final DateTimeFormatter ROW_DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final String ALL_CATEGORIES = "All categories";

    @FXML private Label monthLabel;
    @FXML private Button thisMonthButton;
    @FXML private SummaryStrip summaryStrip;
    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> categoryFilter;
    @FXML private TableView<Expense> expenseTable;
    @FXML private TableColumn<Expense, LocalDate> dateColumn;
    @FXML private TableColumn<Expense, String> descriptionColumn;
    @FXML private TableColumn<Expense, String> categoryColumn;
    @FXML private TableColumn<Expense, Double> amountColumn;
    @FXML private Label resultLabel;
    @FXML private PieChart chart;
    @FXML private Label budgetTitle;
    @FXML private BudgetPanel budgetPanel;

    // Table pipeline: month's expenses -> filtered by search/category -> sorted by column clicks
    private final ObservableList<Expense> monthExpenses = FXCollections.observableArrayList();
    private final FilteredList<Expense> filteredExpenses = new FilteredList<>(monthExpenses);
    private final Label tablePlaceholder = new Label();

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
        setUpTable();
        searchField.textProperty().addListener((obs, oldText, newText) -> applyFilter());
        categoryFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilter());

        // Edit/Delete only make sense with a row selected
        editButton.disableProperty().bind(expenseTable.getSelectionModel().selectedItemProperty().isNull());
        deleteButton.disableProperty().bind(expenseTable.getSelectionModel().selectedItemProperty().isNull());

        // Keyboard shortcuts need the scene, which exists only after the window is built
        expenseTable.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) {
                installShortcuts(scene);
            }
        });
        refreshAll();
    }

    private void setUpTable() {
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        dateColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setText(empty || date == null ? null : date.format(ROW_DATE));
            }
        });
        descriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryColumn.setCellFactory(column -> styledTextCell("category-cell"));
        amountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        amountColumn.setCellFactory(column -> new TableCell<>() {
            {
                getStyleClass().add("amount-cell");
            }

            @Override
            protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                setText(empty || amount == null ? null : money.format(amount));
            }
        });

        SortedList<Expense> sorted = new SortedList<>(filteredExpenses);
        sorted.comparatorProperty().bind(expenseTable.comparatorProperty());
        expenseTable.setItems(sorted);
        expenseTable.setPlaceholder(tablePlaceholder);
        tablePlaceholder.getStyleClass().add("muted");
        tablePlaceholder.setWrapText(true);

        // Double-click a row to edit it; right-click for Edit / Delete
        expenseTable.setRowFactory(table -> {
            TableRow<Expense> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    openExpenseDialog(row.getItem());
                }
            });
            MenuItem edit = new MenuItem("Edit");
            edit.setOnAction(e -> openExpenseDialog(row.getItem()));
            MenuItem delete = new MenuItem("Delete");
            delete.setOnAction(e -> deleteExpense(row.getItem()));
            ContextMenu menu = new ContextMenu(edit, delete);
            row.contextMenuProperty().bind(javafx.beans.binding.Bindings
                    .when(row.emptyProperty()).then((ContextMenu) null).otherwise(menu));
            return row;
        });

        expenseTable.setOnKeyPressed(event -> {
            Expense selected = expenseTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            if (event.getCode() == KeyCode.ENTER) {
                openExpenseDialog(selected);
            } else if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
                deleteExpense(selected);
            }
        });
    }

    private static <T> TableCell<Expense, T> styledTextCell(String styleClass) {
        TableCell<Expense, T> cell = new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        };
        cell.getStyleClass().add(styleClass);
        return cell;
    }

    // ⌘ on macOS, Ctrl elsewhere (SHORTCUT_DOWN picks the right one)
    private void installShortcuts(Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN),
                this::handleAddExpense);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN),
                searchField::requestFocus);
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

        loadExpenseData();
        showChart(totals);
        summaryStrip.show(selectedMonth, MonthSummary.of(sum(totals), previousTotal, budgetSummaries), money);
        budgetPanel.show(budgetSummaries, money);
    }

    private static double sum(Map<String, Double> totals) {
        return totals.values().stream().mapToDouble(Double::doubleValue).sum();
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
        expenseTable.getSelectionModel().clearSelection();
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
        if (selected != null) {
            openExpenseDialog(selected);
        }
    }

    @FXML
    private void handleDeleteExpense() {
        Expense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            deleteExpense(selected);
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
        confirm.setContentText(String.format("%s, %s on %s.",
                description, money.format(expense.getAmount()), expense.getDate().format(ROW_DATE)));
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.OK)).setText("Delete");

        confirm.showAndWait().filter(response -> response == ButtonType.OK).ifPresent(response -> {
            expenses.deleteExpense(expense.getId());
            refreshAll();
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
        List<String> items = new ArrayList<>();
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
        int shown = filteredExpenses.size();
        int all = monthExpenses.size();
        resultLabel.setText(filter.isActive()
                ? String.format("Showing %d of %d expenses, %s", shown, all, money.format(shownTotal))
                : String.format("%d expense%s, %s in total", all, all == 1 ? "" : "s", money.format(shownTotal)));

        // Empty states say what to do next
        tablePlaceholder.setText(all == 0
                ? "No expenses in " + selectedMonth.format(MONTH_TITLE) + ". Use Add expense (⌘N) to record one."
                : "No expenses match your search. Clear the search box or choose All categories.");
    }

    // ---------------------------------------------------------------- chart

    private void showChart(Map<String, Double> totals) {
        ObservableList<PieChart.Data> slices = FXCollections.observableArrayList();
        // The amount goes in the legend label: with labels off, the legend is the readable key
        totals.forEach((category, total) ->
                slices.add(new PieChart.Data(category + "  " + money.format(total), total)));
        chart.setData(slices);
        chart.setTitle(totals.isEmpty() ? "Nothing spent in " + selectedMonth.format(MONTH_NAME) : "Spending by category");
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
        Theme.apply(categoryDialog.getDialogPane());
        categoryDialog.setTitle("Set budget");
        categoryDialog.setHeaderText("Which category?");
        categoryDialog.setContentText("Category:");
        Optional<String> category = categoryDialog.showAndWait();
        if (category.isEmpty()) {
            return; // user cancelled
        }

        OptionalDouble current = budgets.getBudget(category.get());
        TextInputDialog amountDialog = new TextInputDialog(
                current.isPresent() ? money.number(current.getAsDouble()).replace(",", "") : "");
        Theme.apply(amountDialog.getDialogPane());
        amountDialog.setTitle("Set budget");
        amountDialog.setHeaderText("Monthly budget for " + category.get());
        amountDialog.setContentText("Amount (" + money.currencyCode() + "):");
        Optional<String> input = amountDialog.showAndWait();
        if (input.isEmpty()) {
            return; // user cancelled
        }

        double limit;
        try {
            limit = Double.parseDouble(input.get().trim().replace(",", ""));
        } catch (NumberFormatException e) {
            limit = -1;
        }
        if (limit <= 0) {
            Alerts.error("Budget not saved", "Enter an amount greater than 0, for example 5000.");
            return;
        }

        budgets.setBudget(category.get(), limit);
        refreshAll();
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
        List<String> choices = new ArrayList<>(SettingsRepository.COMMON_CURRENCIES);
        String current = settings.getCurrency();
        if (!choices.contains(current)) {
            choices.add(0, current);
        }
        ChoiceDialog<String> dialog = new ChoiceDialog<>(current, choices);
        Theme.apply(dialog.getDialogPane());
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
        fileChooser.setTitle("Export expenses");
        fileChooser.setInitialFileName("expenses.csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv"));

        File file = fileChooser.showSaveDialog(expenseTable.getScene().getWindow());
        if (file == null) {
            return; // user cancelled
        }

        try (Writer writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            CsvExporter.write(expenses.getAllExpenses(), writer);
            Alerts.info("Export complete", "All expenses were exported to " + file.getName() + ".");
        } catch (IOException e) {
            Alerts.error("Export failed", "Could not write " + file.getName() + ": " + e.getMessage());
        }
    }
}
