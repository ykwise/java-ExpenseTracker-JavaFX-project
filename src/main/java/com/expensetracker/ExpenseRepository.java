package com.expensetracker;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * All database access for the app: expenses, categories and budgets.
 *
 * Replaces the old all-static DatabaseManager. It is an ordinary object created
 * with a JDBC URL, so the app uses the real database file while tests point it
 * at a throwaway one. Failures throw {@link DataAccessException} instead of
 * being printed and ignored, so the UI can tell the user something went wrong.
 */
public class ExpenseRepository {

    /** Categories created on first run (by migration v1). */
    static final List<String> DEFAULT_CATEGORIES =
            List.of("Food", "Transport", "Utilities", "Entertainment", "Other");

    // "This month" is a half-open date range [first day, first day of next month).
    // Comparing the raw date column (ISO yyyy-MM-dd text sorts chronologically) lets
    // SQLite use an index. The old strftime('%Y-%m', e.date) = ... wrapped the column
    // in a function, which forces a full table scan.
    private static final String IN_MONTH = "e.date >= ? AND e.date < ?";

    private final String url;
    private final Clock clock;

    /** Uses the system clock to decide what "this month" is. */
    public ExpenseRepository(String url) {
        this(url, Clock.systemDefaultZone());
    }

    /** Lets tests pin "today" so month-based queries are deterministic. */
    public ExpenseRepository(String url, Clock clock) {
        this.url = url;
        this.clock = clock;
    }

    private Connection connect() throws SQLException {
        Connection conn = DriverManager.getConnection(url);
        // SQLite ignores FOREIGN KEY clauses unless this is switched on, per connection.
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            conn.close();
            throw e;
        }
        return conn;
    }

    private YearMonth currentMonth() {
        return YearMonth.now(clock);
    }

    // Binds the [start, end) bounds of the current month to two consecutive parameters
    private void bindCurrentMonth(PreparedStatement pstmt, int firstIndex) throws SQLException {
        YearMonth month = currentMonth();
        pstmt.setString(firstIndex, month.atDay(1).toString());
        pstmt.setString(firstIndex + 1, month.plusMonths(1).atDay(1).toString());
    }

    // ---------------------------------------------------------------- schema

    /** Brings the database schema up to date. Safe to call on every start-up. */
    public void initialize() {
        try (Connection conn = connect()) {
            SchemaMigrator.migrate(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Could not initialise the database", e);
        }
    }

    // ------------------------------------------------------------ categories

    public List<String> getAllCategoryNames() {
        List<String> names = new ArrayList<>();
        try (Connection conn = connect();
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

    // Looks up a category's id, failing loudly instead of silently storing NULL
    private int categoryId(Connection conn, String categoryName) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT id FROM Categories WHERE name = ?")) {
            pstmt.setString(1, categoryName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Unknown category: " + categoryName);
                }
                return rs.getInt(1);
            }
        }
    }

    // -------------------------------------------------------------- expenses

    public List<Expense> getAllExpenses() {
        String sql = "SELECT e.id, e.date, c.name AS category, e.amount, e.description "
                + "FROM Expenses e JOIN Categories c ON e.category_id = c.id "
                + "ORDER BY e.date DESC, e.id DESC";
        List<Expense> expenses = new ArrayList<>();
        try (Connection conn = connect();
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
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, date.toString());
            pstmt.setInt(2, categoryId(conn, categoryName));
            pstmt.setDouble(3, amount);
            pstmt.setString(4, description);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the expense", e);
        }
    }

    public void updateExpense(int id, LocalDate date, String categoryName, double amount, String description) {
        String sql = "UPDATE Expenses SET date = ?, category_id = ?, amount = ?, description = ? WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, date.toString());
            pstmt.setInt(2, categoryId(conn, categoryName));
            pstmt.setDouble(3, amount);
            pstmt.setString(4, description);
            pstmt.setInt(5, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the expense", e);
        }
    }

    public void deleteExpense(int id) {
        try (Connection conn = connect();
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
                + "WHERE " + IN_MONTH + " "
                + "GROUP BY c.name ORDER BY c.name";
        Map<String, Double> totals = new LinkedHashMap<>();
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            bindCurrentMonth(pstmt, 1);
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

    // --------------------------------------------------------------- budgets

    /** Creates or replaces the monthly budget for a category. */
    public void setBudget(String categoryName, double monthlyLimit) {
        if (monthlyLimit <= 0) {
            throw new IllegalArgumentException("Budget must be greater than 0");
        }
        String sql = "INSERT INTO Budgets (category_id, monthly_limit) VALUES (?, ?) "
                + "ON CONFLICT(category_id) DO UPDATE SET monthly_limit = excluded.monthly_limit";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, categoryId(conn, categoryName));
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
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, categoryName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? OptionalDouble.of(rs.getDouble(1)) : OptionalDouble.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the budget", e);
        }
    }

    /** Total spent in a category in the current month (0 if nothing). */
    public double getMonthlySpent(String categoryName) {
        String sql = "SELECT COALESCE(SUM(e.amount), 0) FROM Expenses e "
                + "JOIN Categories c ON e.category_id = c.id "
                + "WHERE c.name = ? AND " + IN_MONTH;
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, categoryName);
            bindCurrentMonth(pstmt, 2);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load this month's spending", e);
        }
    }

    /**
     * Every budgeted category with its spend this month, ordered by category name.
     * One query with a LEFT JOIN + GROUP BY, rather than calling getBudget() and
     * getMonthlySpent() per category (the N+1 query pattern: 1 + 2N round trips).
     */
    public List<BudgetSummary> getBudgetSummaries() {
        String sql = "SELECT c.name AS category, b.monthly_limit AS monthly_limit, "
                + "COALESCE(SUM(e.amount), 0) AS spent "
                + "FROM Budgets b "
                + "JOIN Categories c ON b.category_id = c.id "
                + "LEFT JOIN Expenses e ON e.category_id = c.id AND " + IN_MONTH + " "
                + "GROUP BY c.name, b.monthly_limit ORDER BY c.name";
        List<BudgetSummary> summaries = new ArrayList<>();
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            bindCurrentMonth(pstmt, 1);
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
