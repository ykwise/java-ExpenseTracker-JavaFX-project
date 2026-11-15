package com.expensetracker;

import com.expensetracker.BudgetService.BudgetStatus;
import com.expensetracker.suggest.AsyncSuggester;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.OptionalDouble;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.Duration;

/** Controller for the Add / Edit Expense dialog. */
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
    private Label suggestionLabel;

    private final ExpenseRepository expenses;
    private final BudgetRepository budgets;
    private final SettingsRepository settings;
    private final AsyncSuggester suggester;
    private Stage dialogStage;
    private Integer expenseId = null; // null means "Add" mode, a value means "Edit" mode

    // Wait for a pause in typing before asking for a suggestion (debounce): one request
    // per pause instead of one per keystroke, which also limits API calls and cost.
    private final PauseTransition typingPause = new PauseTransition(Duration.millis(600));
    private boolean userChoseCategory = false;    // never override a category the user picked
    private boolean applyingSuggestion = false;   // distinguishes our setValue from the user's

    public AddExpenseController(ExpenseRepository expenses, BudgetRepository budgets,
                                SettingsRepository settings, AsyncSuggester suggester) {
        this.expenses = expenses;
        this.budgets = budgets;
        this.settings = settings;
        this.suggester = suggester;
    }

    @FXML
    private void initialize() {
        categoryComboBox.getItems().addAll(expenses.getAllCategoryNames());
        datePicker.setValue(LocalDate.now()); // most expenses are entered on the day

        categoryComboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!applyingSuggestion && newValue != null) {
                userChoseCategory = true;
                suggester.cancelPending();
                hideSuggestion();
            }
        });

        typingPause.setOnFinished(event -> requestSuggestion());
        descriptionField.textProperty().addListener((obs, oldText, newText) -> {
            if (!userChoseCategory) {
                typingPause.playFromStart();
            }
        });
    }

    private void requestSuggestion() {
        String description = descriptionField.getText();
        if (description == null || description.isBlank()) {
            return;
        }
        // Runs on a background thread; the callback comes back on the JavaFX thread
        suggester.request(description, categoryComboBox.getItems(), suggestion -> {
            if (userChoseCategory || suggestion.isEmpty()) {
                return;
            }
            applyingSuggestion = true;
            categoryComboBox.setValue(suggestion.get());
            applyingSuggestion = false;
            suggestionLabel.setText("Suggested category: " + suggestion.get() + " (you can change it)");
            suggestionLabel.setVisible(true);
            suggestionLabel.setManaged(true);
            if (dialogStage != null) {
                dialogStage.sizeToScene();
            }
        });
    }

    private void hideSuggestion() {
        suggestionLabel.setVisible(false);
        suggestionLabel.setManaged(false);
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
        // Whichever way the dialog closes, drop any suggestion still in flight
        dialogStage.setOnHidden(event -> {
            typingPause.stop();
            suggester.cancelPending();
        });
    }

    /** Switches the dialog to Edit mode and pre-fills it with an existing expense. */
    public void loadExpenseData(Expense expense) {
        this.expenseId = expense.getId();
        this.userChoseCategory = true; // editing: the category was already chosen
        datePicker.setValue(expense.getDate());
        categoryComboBox.setValue(expense.getCategory());
        amountField.setText(String.valueOf(expense.getAmount()));
        descriptionField.setText(expense.getDescription());
    }

    @FXML
    private void handleSave() {
        if (datePicker.getValue() == null || categoryComboBox.getValue() == null || amountField.getText().isBlank()) {
            Alerts.error("Please correct invalid fields", "Date, Category, and Amount are required.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountField.getText().trim());
        } catch (NumberFormatException e) {
            Alerts.error("Invalid Amount", "Please enter a valid number for the amount.");
            return;
        }
        if (amount <= 0) {
            Alerts.error("Invalid Amount", "The amount must be greater than 0.");
            return;
        }

        LocalDate date = datePicker.getValue();
        String category = categoryComboBox.getValue();
        String description = descriptionField.getText();

        if (expenseId == null) {
            expenses.addExpense(date, category, amount, description);
        } else {
            expenses.updateExpense(expenseId, date, category, amount, description);
        }

        showBudgetAlertIfNeeded(date, category);
        dialogStage.close();
    }

    // Warns (but does not block) when this month's spending in the category nears or passes its budget
    private void showBudgetAlertIfNeeded(LocalDate date, String category) {
        YearMonth month = YearMonth.from(date);
        if (!month.equals(YearMonth.now())) {
            return; // only warn about the month that is still in progress; past months can't be changed
        }

        OptionalDouble budget = budgets.getBudget(category);
        double spent = budgets.getMonthlySpent(category, month);
        BudgetStatus status = BudgetService.evaluate(spent, budget);

        if (status == BudgetStatus.WARNING || status == BudgetStatus.EXCEEDED) {
            double limit = budget.getAsDouble();
            MoneyFormat money = settings.moneyFormat();
            Alerts.warning("Budget Alert",
                    status == BudgetStatus.EXCEEDED
                            ? category + " budget exceeded"
                            : category + " budget almost used up",
                    String.format("Spent %s of %s this month (%.0f%%).",
                            money.format(spent), money.format(limit), spent / limit * 100));
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }
}
