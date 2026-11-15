package com.expensetracker;

import java.util.List;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/**
 * Spend vs budget per category, one row each: name, "850.00 of 1,000.00", and a bar
 * whose colour follows the {@link BudgetService} status. Extracted from MainViewController,
 * which CKJM had flagged as the least cohesive class.
 */
public class BudgetPanel extends VBox {

    public BudgetPanel() {
        super(14);
        getStyleClass().add("budget-panel");
    }

    public void show(List<BudgetSummary> summaries, MoneyFormat money) {
        getChildren().clear();
        if (summaries.isEmpty()) {
            Label empty = new Label("No budgets yet. Use Set budget to give a category a monthly limit.");
            empty.setWrapText(true);
            empty.getStyleClass().add("muted");
            getChildren().add(empty);
            return;
        }
        for (BudgetSummary summary : summaries) {
            getChildren().add(row(summary, money));
        }
    }

    private static VBox row(BudgetSummary summary, MoneyFormat money) {
        Label name = new Label(summary.category());
        name.getStyleClass().add("budget-name");
        Label amounts = new Label(money.number(summary.spent()) + " of " + money.format(summary.monthlyLimit()));
        amounts.getStyleClass().add("budget-amounts");

        BorderPane header = new BorderPane();
        header.setLeft(name);
        header.setRight(amounts);

        ProgressBar bar = new ProgressBar(Math.min(summary.usedRatio(), 1.0));
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add(switch (summary.status()) {
            case EXCEEDED -> "budget-over";
            case WARNING -> "budget-near";
            default -> "budget-ok";
        });

        VBox row = new VBox(6, header, bar);
        row.getStyleClass().add("budget-row");
        return row;
    }
}
