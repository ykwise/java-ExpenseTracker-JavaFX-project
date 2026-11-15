package com.expensetracker;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes expenses and categories.
 *
 * Failures throw {@link DataAccessException} instead of being printed and ignored,
 * so the UI can tell the user something went wrong.
 */
public class ExpenseRepository {

    private final Database db;

    public ExpenseRepository(Database db) {
        this.db = db;
    }

    // ------------------------------------------------------------ categories

    public List<String> getAllCategoryNames() {
        List<String> names = new ArrayList<>();
        try (Connection conn = db.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM Categories ORDER BY name")) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load categories", e);
        }
        return names;
    }

    // -------------------------------------------------------------- expenses

    public List<Expense> getAllExpenses() {
        String sql = "SELECT e.id, e.date, c.name AS category, e.amount, e.description "
                + "FROM Expenses e JOIN Categories c ON e.category_id = c.id "
                + "ORDER BY e.date DESC, e.id DESC";
        List<Expense> expenses = new ArrayList<>();
        try (Connection conn = db.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                expenses.add(new Expense(
                        rs.getInt("id"),
                        LocalDate.parse(rs.getString("date")),
                        rs.getString("category"),
                        rs.getDouble("amount"),
                        rs.getString("description")));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load expenses", e);
        }
        return expenses;
    }

    public void addExpense(LocalDate date, String categoryName, double amount, String description) {
        String sql = "INSERT INTO Expenses (date, category_id, amount, description) VALUES (?, ?, ?, ?)";
        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, date.toString());
            pstmt.setInt(2, db.categoryId(conn, categoryName));
            pstmt.setDouble(3, amount);
            pstmt.setString(4, description);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the expense", e);
        }
    }

    public void updateExpense(int id, LocalDate date, String categoryName, double amount, String description) {
        String sql = "UPDATE Expenses SET date = ?, category_id = ?, amount = ?, description = ? WHERE id = ?";
        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, date.toString());
            pstmt.setInt(2, db.categoryId(conn, categoryName));
            pstmt.setDouble(3, amount);
            pstmt.setString(4, description);
            pstmt.setInt(5, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the expense", e);
        }
    }

    public void deleteExpense(int id) {
        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM Expenses WHERE id = ?")) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the expense", e);
        }
    }

    /** Total spent per category in the current month, ordered by category name. */
    public Map<String, Double> getMonthlyCategorySummary() {
        String sql = "SELECT c.name AS category, SUM(e.amount) AS total "
                + "FROM Expenses e JOIN Categories c ON e.category_id = c.id "
                + "WHERE " + Database.IN_MONTH + " "
                + "GROUP BY c.name ORDER BY c.name";
        Map<String, Double> totals = new LinkedHashMap<>();
        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            db.bindCurrentMonth(pstmt, 1);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    totals.put(rs.getString("category"), rs.getDouble("total"));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the monthly summary", e);
        }
        return totals;
    }
}
