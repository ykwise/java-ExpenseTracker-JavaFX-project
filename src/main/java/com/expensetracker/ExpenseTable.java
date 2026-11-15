package com.expensetracker;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;

/**
 * Everything about the expense list: columns and formatting, the
 * month -> filtered -> sorted pipeline, search and category filter, the result line,
 * empty-state text, and row interactions (double-click, context menu, Enter, Delete).
 *
 * Extracted from MainViewController, which CKJM showed growing into the least
 * cohesive class in the app. The controller now only hands it data and actions.
 */
final class ExpenseTable {

    private static final DateTimeFormatter ROW_DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    static final String ALL_CATEGORIES = "All categories";

    private final TableView<Expense> table;
    private final TextField searchField;
    private final ComboBox<String> categoryFilter;
    private final Label resultLabel;
    private final Label placeholder = new Label();

    private final ObservableList<Expense> monthExpenses = FXCollections.observableArrayList();
    private final FilteredList<Expense> filtered = new FilteredList<>(monthExpenses);

    private Supplier<MoneyFormat> money;
    private YearMonth month = YearMonth.now();

    @SuppressWarnings("unchecked")
    ExpenseTable(TableView<Expense> table, TextField searchField, ComboBox<String> categoryFilter, Label resultLabel,
                 Supplier<MoneyFormat> money, Consumer<Expense> onEdit, Consumer<Expense> onDelete) {
        this.table = table;
        this.searchField = searchField;
        this.categoryFilter = categoryFilter;
        this.resultLabel = resultLabel;
        this.money = money;

        List<TableColumn<Expense, ?>> columns = table.getColumns();
        setUpColumns((TableColumn<Expense, LocalDate>) columns.get(0), (TableColumn<Expense, String>) columns.get(1),
                (TableColumn<Expense, String>) columns.get(2), (TableColumn<Expense, Double>) columns.get(3));

        SortedList<Expense> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.setPlaceholder(placeholder);
        placeholder.getStyleClass().add("muted");
        placeholder.setWrapText(true);

        table.setRowFactory(t -> row(onEdit, onDelete));
        table.setOnKeyPressed(event -> {
            Expense selected = selected();
            if (selected == null) {
                return;
            }
            if (event.getCode() == KeyCode.ENTER) {
                onEdit.accept(selected);
            } else if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
                onDelete.accept(selected);
            }
        });

        searchField.textProperty().addListener((obs, oldText, newText) -> applyFilter());
        categoryFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilter());
    }

    private void setUpColumns(TableColumn<Expense, LocalDate> date, TableColumn<Expense, String> description,
                              TableColumn<Expense, String> category, TableColumn<Expense, Double> amount) {
        date.setCellValueFactory(new PropertyValueFactory<>("date"));
        date.setCellFactory(c -> textCell(d -> d.format(ROW_DATE), null));
        description.setCellValueFactory(new PropertyValueFactory<>("description"));
        category.setCellValueFactory(new PropertyValueFactory<>("category"));
        category.setCellFactory(c -> textCell(String::valueOf, "category-cell"));
        amount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        amount.setCellFactory(c -> textCell(a -> money.get().format(a), "amount-cell"));
    }

    private static <T> TableCell<Expense, T> textCell(java.util.function.Function<T, String> format, String style) {
        TableCell<Expense, T> cell = new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : format.apply(item));
            }
        };
        if (style != null) {
            cell.getStyleClass().add(style);
        }
        return cell;
    }

    // Double-click a row to edit it; right-click for Edit / Delete
    private static TableRow<Expense> row(Consumer<Expense> onEdit, Consumer<Expense> onDelete) {
        TableRow<Expense> row = new TableRow<>();
        row.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                onEdit.accept(row.getItem());
            }
        });
        MenuItem edit = new MenuItem("Edit");
        edit.setOnAction(e -> onEdit.accept(row.getItem()));
        MenuItem delete = new MenuItem("Delete");
        delete.setOnAction(e -> onDelete.accept(row.getItem()));
        ContextMenu menu = new ContextMenu(edit, delete);
        row.contextMenuProperty().bind(Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(menu));
        return row;
    }

    /** Replaces the rows with {@code expenses} from {@code month} and refreshes the category choices. */
    void show(YearMonth month, List<Expense> expenses, List<String> categoryNames) {
        this.month = month;
        String chosen = categoryFilter.getValue();
        List<String> items = new ArrayList<>();
        items.add(ALL_CATEGORIES);
        items.addAll(categoryNames);
        categoryFilter.getItems().setAll(items);
        categoryFilter.setValue(items.contains(chosen) ? chosen : ALL_CATEGORIES);

        monthExpenses.setAll(expenses);
        table.refresh(); // re-render amounts if the currency changed
        applyFilter();
    }

    Expense selected() {
        return table.getSelectionModel().getSelectedItem();
    }

    ObservableBooleanValue noSelection() {
        return table.getSelectionModel().selectedItemProperty().isNull();
    }

    void clearSelection() {
        table.getSelectionModel().clearSelection();
    }

    void focusSearch() {
        searchField.requestFocus();
    }

    private void applyFilter() {
        String category = categoryFilter.getValue();
        ExpenseFilter filter = new ExpenseFilter(searchField.getText(),
                category == null || ALL_CATEGORIES.equals(category) ? null : category);
        filtered.setPredicate(filter);

        MoneyFormat format = money.get();
        double shownTotal = filtered.stream().mapToDouble(Expense::getAmount).sum();
        int shown = filtered.size();
        int all = monthExpenses.size();
        resultLabel.setText(filter.isActive()
                ? String.format("Showing %d of %d expenses, %s", shown, all, format.format(shownTotal))
                : String.format("%d expense%s, %s in total", all, all == 1 ? "" : "s", format.format(shownTotal)));

        // Empty states say what to do next
        placeholder.setText(all == 0
                ? "No expenses in " + month.format(MONTH_TITLE) + ". Use Add expense (⌘N) to record one."
                : "No expenses match your search. Clear the search box or choose All categories.");
    }
}
