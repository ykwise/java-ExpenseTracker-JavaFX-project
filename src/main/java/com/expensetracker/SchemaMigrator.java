package com.expensetracker;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Versioned schema migrations.
 *
 * The database records which migrations it has already run in SQLite's built-in
 * {@code PRAGMA user_version}. On start-up we run only the missing ones, in order.
 * Each migration runs inside a transaction together with its version bump, so a
 * failure part-way through rolls back completely and the database is never left
 * half-upgraded (the A and C in ACID).
 *
 * Rules for adding a migration: append to {@link #MIGRATIONS}; never edit or
 * reorder one that has shipped, because users' databases have already run it.
 */
final class SchemaMigrator {

    /** Each entry is one migration: a list of SQL statements. Index + 1 = schema version. */
    static final List<List<String>> MIGRATIONS = List.of(
            // v1: original tables. IF NOT EXISTS because databases created by the
            // pre-migration version of the app already have them (at user_version 0).
            List.of(
                    "CREATE TABLE IF NOT EXISTS Categories ("
                            + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + " name TEXT NOT NULL UNIQUE)",
                    "CREATE TABLE IF NOT EXISTS Expenses ("
                            + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + " amount DECIMAL(10, 2) NOT NULL,"
                            + " date DATE NOT NULL,"
                            + " description TEXT,"
                            + " category_id INTEGER,"
                            + " FOREIGN KEY (category_id) REFERENCES Categories (id))",
                    "INSERT OR IGNORE INTO Categories (name) VALUES "
                            + "('Food'), ('Transport'), ('Utilities'), ('Entertainment'), ('Other')"),

            // v2: budgets. Primary key on category_id = at most one budget per category.
            List.of(
                    "CREATE TABLE IF NOT EXISTS Budgets ("
                            + " category_id INTEGER PRIMARY KEY,"
                            + " monthly_limit REAL NOT NULL CHECK (monthly_limit > 0),"
                            + " FOREIGN KEY (category_id) REFERENCES Categories (id))"),

            // v3: index for the "this month, per category" queries. Leading on
            // category_id serves the per-category lookups; date as the second column
            // lets SQLite range-scan within a category instead of reading every row.
            List.of(
                    "CREATE INDEX IF NOT EXISTS idx_expenses_category_date ON Expenses (category_id, date)",
                    "CREATE INDEX IF NOT EXISTS idx_expenses_date ON Expenses (date)"),

            // v4: key/value settings, starting with the display currency.
            List.of(
                    "CREATE TABLE IF NOT EXISTS Settings ("
                            + " key TEXT PRIMARY KEY,"
                            + " value TEXT NOT NULL)",
                    "INSERT OR IGNORE INTO Settings (key, value) VALUES ('currency', 'KES')"));

    private SchemaMigrator() {
    }

    static int latestVersion() {
        return MIGRATIONS.size();
    }

    static int currentVersion(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Runs every migration the database has not seen yet. */
    static void migrate(Connection conn) throws SQLException {
        int version = currentVersion(conn);
        for (int i = version; i < MIGRATIONS.size(); i++) {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                for (String sql : MIGRATIONS.get(i)) {
                    stmt.execute(sql);
                }
                // PRAGMA can't take a bound parameter; the value is our own int, not user input
                stmt.execute("PRAGMA user_version = " + (i + 1));
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(previousAutoCommit);
            }
        }
    }
}
