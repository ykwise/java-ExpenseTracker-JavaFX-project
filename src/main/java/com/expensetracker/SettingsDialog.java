package com.expensetracker;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.ChoiceDialog;

/** Lets the user choose the display currency. */
final class SettingsDialog {

    private SettingsDialog() {
    }

    /** @return true if the setting changed */
    static boolean show(SettingsRepository settings) {
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
        return dialog.showAndWait().filter(code -> !code.equals(current)).map(code -> {
            settings.setCurrency(code);
            return true;
        }).orElse(false);
    }
}
