package com.expensetracker;

import java.util.Objects;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;

/** Applies the app stylesheet to windows and dialogs, so everything shares one look. */
final class Theme {

    private static final String STYLESHEET =
            Objects.requireNonNull(Theme.class.getResource("app.css"), "app.css missing").toExternalForm();

    private Theme() {
    }

    static void apply(Scene scene) {
        if (!scene.getStylesheets().contains(STYLESHEET)) {
            scene.getStylesheets().add(STYLESHEET);
        }
    }

    static void apply(DialogPane pane) {
        if (!pane.getStylesheets().contains(STYLESHEET)) {
            pane.getStylesheets().add(STYLESHEET);
        }
    }
}
