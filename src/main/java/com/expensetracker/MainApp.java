package com.expensetracker;

import java.io.IOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Callback;

/**
 * Entry point. Also the "composition root": the one place that creates the
 * shared objects and hands them to the controllers that need them.
 */
public class MainApp extends Application {

    /** Override with -Dexpensetracker.db=/path/to/file.db to use a different database file. */
    static final String DB_PATH = System.getProperty("expensetracker.db", "expenses.db");

    @Override
    public void start(Stage primaryStage) throws IOException {
        // Any unexpected error from a button handler (e.g. the database file is locked)
        // is shown to the user instead of vanishing into the console.
        Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace();
            Alerts.error("Something went wrong", userMessage(error));
        });

        ExpenseRepository repository = new ExpenseRepository("jdbc:sqlite:" + DB_PATH);
        repository.initialize();

        Callback<Class<?>, Object> controllers = new Callback<>() {
            @Override
            public Object call(Class<?> type) {
                if (type == MainViewController.class) {
                    return new MainViewController(repository, this);
                }
                if (type == AddExpenseController.class) {
                    return new AddExpenseController(repository);
                }
                throw new IllegalArgumentException("No controller registered for " + type);
            }
        };

        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("MainView.fxml"));
        loader.setControllerFactory(controllers);
        Parent root = loader.load();

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("Expense Tracker");
        primaryStage.show();
    }

    // FXML handlers wrap exceptions (RuntimeException -> InvocationTargetException -> ...),
    // so walk the chain and prefer our own, user-friendly message when there is one.
    static String userMessage(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof DataAccessException || t instanceof IllegalArgumentException) {
                return t.getMessage();
            }
        }
        Throwable root = error;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
