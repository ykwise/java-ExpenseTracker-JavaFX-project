package com.expensetracker;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes expenses.
 *
 * Failures throw {@link DataAccessException} instead of being printed and ignored,
 * so the UI can tell the user something went wrong.
 */
public class ExpenseRepository {

    private final Database db;

    public ExpenseRepository(Database db) {
        this.db = db;
    }

    // -------------------------------------------------------------- expenses

    /** Every expense, newest first (used for CSV export). */
    public List<Expense> getAllExpenses() {
        return queryExpenses("", null);
    }

    /** Expenses dated in {@code month}, newest first. */
    public List<Expense> getExpenses(YearMonth month) {
        return queryExpenses("WHERE " + Database.IN_MONTH + " ", month);
    }

    private List<Expense> queryExpenses(String where, YearMonth month) {
        String sql = "SELECT e.id, e.date, c.name AS category, e.amount, e.description "
                + "FROM Expenses e JOIN Categories c ON e.category_id = c.id "
                + where
                + "ORDER BY e.date DESC, e.id DESC";
        List<Expense> result = new ArrayList<>();
        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (month != null) {
                db.bindMonth(pstmt, 1, month);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new Expense(
                            rs.getInt("id"),
                            LocalDate.parse(rs.getString("date")),
                            rs.getString("category"),
                            rs.getDouble("amount"),
                            rs.getString("description")));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load expenses", e);
        }
        return result;
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

    /** Total spent per category in {@code month}, ordered by category name. */
    public Map<String, Double> getMonthlyCategorySummary(YearMonth month) {
        String sql = "SELECT c.name AS category, SUM(e.amount) AS total "
                + "FROM Expenses e JOIN Categories c ON e.category_id = c.id "
                + "WHERE " + Database.IN_MONTH + " "
                + "GROUP BY c.name ORDER BY c.name";
        Map<String, Double> totals = new LinkedHashMap<>();
        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            db.bindMonth(pstmt, 1, month);
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
