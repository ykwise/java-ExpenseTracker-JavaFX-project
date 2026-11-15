package com.expensetracker;

import com.expensetracker.suggest.AsyncSuggester;
import com.expensetracker.suggest.CategorySuggester;
import com.expensetracker.suggest.Suggesters;
import java.io.IOException;
import javafx.application.Application;
import javafx.application.Platform;
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

    private AsyncSuggester suggester;

    @Override
    public void start(Stage primaryStage) throws IOException {
        // Any unexpected error from a button handler (e.g. the database file is locked)
        // is shown to the user instead of vanishing into the console.
        Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace();
            Alerts.error("Something went wrong", userMessage(error));
        });

        // A fixed per-user location, so starting the app from another folder can't
        // open an empty database (see DataLocation for the rules and legacy copy)
        DataLocation.Result location = DataLocation.resolve();
        Database database = new Database("jdbc:sqlite:" + location.databaseFile());
        database.initialize();
        ExpenseRepository expenses = new ExpenseRepository(database);
        BudgetRepository budgets = new BudgetRepository(database);
        SettingsRepository settings = new SettingsRepository(database);
        CategoryRepository categories = new CategoryRepository(database);

        // Category suggestions: Claude if ANTHROPIC_API_KEY is set, offline keywords otherwise.
        // Work runs on a background thread; results are handed back to the JavaFX thread.
        CategorySuggester categorySuggester = Suggesters.create(System.getenv());
        suggester = new AsyncSuggester(categorySuggester, Platform::runLater);
        AsyncSuggester asyncSuggester = suggester;

        Callback<Class<?>, Object> controllers = new Callback<>() {
            @Override
            public Object call(Class<?> type) {
                if (type == MainViewController.class) {
                    return new MainViewController(expenses, budgets, categories, settings, this);
                }
                if (type == AddExpenseController.class) {
                    return new AddExpenseController(expenses, budgets, categories, settings, asyncSuggester);
                }
                throw new IllegalArgumentException("No controller registered for " + type);
            }
        };

        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("MainView.fxml"));
        loader.setControllerFactory(controllers);
        Parent root = loader.load();

        Scene scene = new Scene(root);
        Theme.apply(scene);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(980);
        primaryStage.setMinHeight(640);
        primaryStage.setTitle(Suggesters.aiEnabled(System.getenv())
                ? "Expense Tracker (AI suggestions on)" : "Expense Tracker");
        primaryStage.show();

        location.copiedFrom().ifPresent(from -> Alerts.info("Your data has moved",
                "Expense Tracker now keeps its data in one place, whichever folder it is started from:\n"
                        + location.databaseFile() + "\n\nYour existing data was copied there. The old file was left "
                        + "untouched as a backup:\n" + from));
    }

    /** Graceful shutdown: called by JavaFX when the window closes. */
    @Override
    public void stop() {
        if (suggester != null) {
            suggester.close();
        }
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
