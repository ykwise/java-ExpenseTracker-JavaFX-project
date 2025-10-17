package com.expensetracker;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import java.time.LocalDate;

public class Expense {

    private final SimpleIntegerProperty id;
    private final SimpleObjectProperty<LocalDate> date;
    private final SimpleStringProperty category;
    private final SimpleDoubleProperty amount;
    private final SimpleStringProperty description;

    public Expense(int id, LocalDate date, String category, double amount, String description) {
        this.id = new SimpleIntegerProperty(id);
        this.date = new SimpleObjectProperty<>(date);
        this.category = new SimpleStringProperty(category);
        this.amount = new SimpleDoubleProperty(amount);
        this.description = new SimpleStringProperty(description);
    }

    // --- Getters ---

    public int getId() {
        return id.get();
    }

    public LocalDate getDate() {
        return date.get();
    }

    public String getCategory() {
        return category.get();
    }

    public double getAmount() {
        return amount.get();
    }

    public String getDescription() {
        return description.get();
    }
}