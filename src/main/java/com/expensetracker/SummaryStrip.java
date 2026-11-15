package com.expensetracker;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.OptionalDouble;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * One horizontal band with the month's three headline figures, separated by rules:
 * how much was spent, the change from last month, and budget health.
 * A UI component with no data access; the controller hands it a {@link MonthSummary}.
 */
public class SummaryStrip extends HBox {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH);

    private final Label spentValue = new Label();
    private final Label spentCaption = new Label();
    private final Label changeValue = new Label();
    private final Label changeCaption = new Label();
    private final Label budgetValue = new Label();
    private final Label budgetCaption = new Label();

    public SummaryStrip() {
        getStyleClass().add("summary-strip");
        setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(
                figure(spentValue, spentCaption),
                rule(),
                figure(changeValue, changeCaption),
                rule(),
                figure(budgetValue, budgetCaption));
    }

    private static VBox figure(Label value, Label caption) {
        value.getStyleClass().add("figure-value");
        caption.getStyleClass().add("figure-caption");
        VBox box = new VBox(2, value, caption);
        box.getStyleClass().add("figure");
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static Region rule() {
        Region rule = new Region();
        rule.getStyleClass().add("figure-rule");
        return rule;
    }

    public void show(YearMonth month, MonthSummary summary, MoneyFormat money) {
        spentValue.setText(money.format(summary.spent()));
        spentCaption.setText("Spent in " + month.format(MONTH));

        String previous = month.minusMonths(1).format(MONTH);
        OptionalDouble change = summary.changeFromPrevious();
        changeValue.getStyleClass().removeAll("up", "down");
        if (change.isPresent()) {
            double pct = change.getAsDouble() * 100;
            changeValue.setText(String.format("%s%.0f%%", pct > 0 ? "+" : "", pct));
            changeValue.getStyleClass().add(pct > 0 ? "up" : "down"); // spending more is the "bad" direction
            changeCaption.setText("Compared with " + previous);
        } else {
            changeValue.setText("–");
            changeCaption.setText("Nothing spent in " + previous);
        }

        budgetValue.getStyleClass().removeAll("up", "down");
        if (summary.budgetCount() == 0) {
            budgetValue.setText("–");
            budgetCaption.setText("No budgets set");
        } else if (summary.budgetsOver() > 0) {
            budgetValue.setText(summary.budgetsOver() + " over");
            budgetValue.getStyleClass().add("up");
            budgetCaption.setText(summary.budgetsOnTrack() + summary.budgetsNearLimit() + " of "
                    + summary.budgetCount() + " budgets within limit");
        } else {
            budgetValue.setText(summary.budgetsOnTrack() + " of " + summary.budgetCount());
            budgetValue.getStyleClass().add("down");
            budgetCaption.setText(summary.budgetsNearLimit() > 0
                    ? "Budgets on track, " + summary.budgetsNearLimit() + " near the limit"
                    : "Budgets on track");
        }
    }
}
