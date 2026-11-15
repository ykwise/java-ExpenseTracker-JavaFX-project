package com.expensetracker;

import javafx.scene.control.Alert;

/** Small helpers so controllers don't repeat the same four lines for every dialog. */
final class Alerts {

    private Alerts() {
    }

    static void error(String header, String content) {
        show(Alert.AlertType.ERROR, "Error", header, content);
    }

    static void warning(String title, String header, String content) {
        show(Alert.AlertType.WARNING, title, header, content);
    }

    static void info(String title, String content) {
        show(Alert.AlertType.INFORMATION, title, null, content);
    }

    private static void show(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
