package com.expensetracker;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Reads and writes monthly budgets, and the spending figures they are compared with.
 *
 * Split out of ExpenseRepository after CKJM showed it had very low cohesion
 * (LCOM 149): budget queries and expense CRUD share no logic beyond the connection.
 */
public class BudgetRepository {

    private final Database db;

    public BudgetRepository(Database db) {
        this.db = db;
    }

    /** Creates or replaces the monthly budget for a category. */
    public void setBudget(String categoryName, double monthlyLimit) {
        if (monthlyLimit <= 0) {
            throw new IllegalArgumentException("Budget must be greater than 0");
        }
        String sql = "INSERT INTO Budgets (category_id, monthly_limit) VALUES (?, ?) "
                + "ON CONFLICT(category_id) DO UPDATE SET monthly_limit = excluded.monthly_limit";
        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, db.categoryId(conn, categoryName));
            pstmt.setDouble(2, monthlyLimit);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the budget", e);
        }
    }

    /** The category's budget, or empty if none is set. */
    public OptionalDouble getBudget(String categoryName) {
        String sql = "SELECT b.monthly_limit FROM Budgets b "
                + "JOIN Categories c ON b.category_id = c.id WHERE c.name = ?";
        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, categoryName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? OptionalDouble.of(rs.getDouble(1)) : OptionalDouble.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the budget", e);
        }
    }

    /** Total spent in a category in {@code month} (0 if nothing). */
    public double getMonthlySpent(String categoryName, YearMonth month) {
        String sql = "SELECT COALESCE(SUM(e.amount), 0) FROM Expenses e "
                + "JOIN Categories c ON e.category_id = c.id "
                + "WHERE c.name = ? AND " + Database.IN_MONTH;
        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, categoryName);
            db.bindMonth(pstmt, 2, month);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load this month's spending", e);
        }
    }

    /**
     * Every budgeted category with its spend in {@code month}, ordered by category name.
     * One query with a LEFT JOIN + GROUP BY, rather than calling getBudget() and
     * getMonthlySpent() per category (the N+1 query pattern: 1 + 2N round trips).
     */
    public List<BudgetSummary> getBudgetSummaries(YearMonth month) {
        String sql = "SELECT c.name AS category, b.monthly_limit AS monthly_limit, "
                + "COALESCE(SUM(e.amount), 0) AS spent "
                + "FROM Budgets b "
                + "JOIN Categories c ON b.category_id = c.id "
                + "LEFT JOIN Expenses e ON e.category_id = c.id AND " + Database.IN_MONTH + " "
                + "GROUP BY c.name, b.monthly_limit ORDER BY c.name";
        List<BudgetSummary> summaries = new ArrayList<>();
        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            db.bindMonth(pstmt, 1, month);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    summaries.add(new BudgetSummary(
                            rs.getString("category"), rs.getDouble("monthly_limit"), rs.getDouble("spent")));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load budgets", e);
        }
        return summaries;
    }
}
