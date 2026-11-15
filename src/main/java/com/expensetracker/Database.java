package com.expensetracker;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.YearMonth;

/**
 * The shared plumbing every repository needs: opening connections (with foreign keys
 * on), running schema migrations, resolving category names and month bounds.
 *
 * Repositories hold a Database rather than a URL, so they share one configuration
 * and tests can point all of them at the same throwaway file.
 */
public class Database {

    // "This month" is a half-open date range [first day, first day of next month).
    // Comparing the raw date column (ISO yyyy-MM-dd text sorts chronologically) lets
    // SQLite use an index. The old strftime('%Y-%m', e.date) = ... wrapped the column
    // in a function, which forces a full table scan.
    static final String IN_MONTH = "e.date >= ? AND e.date < ?";

    private final String url;

    public Database(String url) {
        this.url = url;
    }

    Connection connect() throws SQLException {
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

    /** Brings the database schema up to date. Safe to call on every start-up. */
    public void initialize() {
        try (Connection conn = connect()) {
            SchemaMigrator.migrate(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Could not initialise the database", e);
        }
    }

    // Binds the [start, end) bounds of a month to two consecutive parameters.
    // Repositories take the month as an argument instead of reading the clock, so the
    // UI decides which month is shown and queries stay deterministic in tests.
    void bindMonth(PreparedStatement pstmt, int firstIndex, YearMonth month) throws SQLException {
        pstmt.setString(firstIndex, month.atDay(1).toString());
        pstmt.setString(firstIndex + 1, month.plusMonths(1).atDay(1).toString());
    }

    // Looks up a category's id, failing loudly instead of silently storing NULL
    int categoryId(Connection conn, String categoryName) throws SQLException {
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
}
