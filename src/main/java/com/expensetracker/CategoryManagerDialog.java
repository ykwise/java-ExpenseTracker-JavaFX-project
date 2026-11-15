package com.expensetracker;

import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** A small dialog listing categories with Add, Rename and Delete. */
final class CategoryManagerDialog {

    private final CategoryRepository categories;
    private final ListView<String> list = new ListView<>();

    CategoryManagerDialog(CategoryRepository categories) {
        this.categories = categories;
    }

    /** Shows the dialog and blocks until it is closed. */
    void showAndWait(Window owner) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Categories");
        dialog.setHeaderText("Add, rename or delete categories");

        Button add = new Button("Add…");
        Button rename = new Button("Rename…");
        Button delete = new Button("Delete");
        rename.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());

        add.setOnAction(e -> handleAdd());
        rename.setOnAction(e -> handleRename());
        delete.setOnAction(e -> handleDelete());
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && list.getSelectionModel().getSelectedItem() != null) {
                handleRename();
            }
        });

        Label hint = new Label("Categories used by expenses can be renamed but not deleted.");
        hint.getStyleClass().add("muted");

        VBox buttons = new VBox(8, add, rename, delete);
        buttons.getChildren().forEach(b -> ((Button) b).setMaxWidth(Double.MAX_VALUE));
        HBox body = new HBox(12, list, buttons);
        HBox.setHgrow(list, Priority.ALWAYS);
        list.setPrefSize(260, 240);

        VBox content = new VBox(10, body, hint);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        reload(null);
        dialog.showAndWait();
    }

    private void reload(String select) {
        list.getItems().setAll(categories.getAllNames());
        if (select != null) {
            list.getSelectionModel().select(select);
        }
    }

    private void handleAdd() {
        prompt("Add category", "New category name:", "").ifPresent(name -> run(() -> {
            categories.add(name);
            reload(CategoryRepository.validName(name));
        }));
    }

    private void handleRename() {
        String current = list.getSelectionModel().getSelectedItem();
        prompt("Rename category", "New name for \"" + current + "\":", current).ifPresent(name -> run(() -> {
            if (!name.trim().equals(current)) {
                categories.rename(current, name);
            }
            reload(CategoryRepository.validName(name));
        }));
    }

    private void handleDelete() {
        String current = list.getSelectionModel().getSelectedItem();
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + current + "\"? Its budget, if any, is removed too.", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText("Delete category");
        confirm.initOwner(list.getScene().getWindow());
        confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> run(() -> {
            categories.delete(current);
            reload(null);
        }));
    }

    private Optional<String> prompt(String title, String label, String initial) {
        TextInputDialog input = new TextInputDialog(initial);
        input.initOwner(list.getScene().getWindow());
        input.setTitle(title);
        input.setHeaderText(null);
        input.setContentText(label);
        return input.showAndWait();
    }

    // Validation problems (empty, duplicate, in use) are expected: show them, don't crash
    private void run(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException e) {
            Alerts.error("Can't do that", e.getMessage());
        }
    }
}
