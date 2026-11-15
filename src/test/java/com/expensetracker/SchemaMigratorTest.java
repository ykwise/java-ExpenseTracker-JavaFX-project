package com.expensetracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaMigratorTest {

    @TempDir
    Path tempDir;

    private Connection open(String name) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + tempDir.resolve(name));
    }

    @Test
    void freshDatabase_isMigratedToLatestVersion() throws SQLException {
        try (Connection conn = open("fresh.db")) {
            SchemaMigrator.migrate(conn);
            assertEquals(SchemaMigrator.latestVersion(), SchemaMigrator.currentVersion(conn));
        }
    }

    @Test
    void databaseFromOriginalApp_isUpgradedWithoutLosingData() throws SQLException {
        try (Connection conn = open("legacy.db"); Statement stmt = conn.createStatement()) {
            // Recreate what the pre-migration app left behind: tables, data, user_version 0
            stmt.execute("CREATE TABLE Categories (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)");
            stmt.execute("CREATE TABLE Expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, amount DECIMAL(10, 2) NOT NULL,"
                    + " date DATE NOT NULL, description TEXT, category_id INTEGER)");
            stmt.execute("INSERT INTO Categories (name) VALUES ('Food')");
            stmt.execute("INSERT INTO Expenses (amount, date, description, category_id) VALUES (20, '2025-10-18', 'Tea', 1)");

            SchemaMigrator.migrate(conn);

            assertEquals(SchemaMigrator.latestVersion(), SchemaMigrator.currentVersion(conn));
            try (ResultSet rs = stmt.executeQuery("SELECT description FROM Expenses")) {
                assertTrue(rs.next());
                assertEquals("Tea", rs.getString(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM Categories")) {
                assertEquals(5, rs.getInt(1)); // existing 'Food' kept, the other defaults added
            }
            try (ResultSet rs = stmt.executeQuery("SELECT value FROM Settings WHERE key = 'currency'")) {
                assertEquals("KES", rs.getString(1));
            }
        }
    }

    @Test
    void runningTwice_doesNothingTheSecondTime() throws SQLException {
        try (Connection conn = open("twice.db")) {
            SchemaMigrator.migrate(conn);
            SchemaMigrator.migrate(conn);
            assertEquals(SchemaMigrator.latestVersion(), SchemaMigrator.currentVersion(conn));
        }
    }

    @Test
    void failedMigration_rollsBackCompletely() throws SQLException {
        try (Connection conn = open("broken.db"); Statement stmt = conn.createStatement()) {
            // Make migration v1's last statement (the category seed INSERT) fail, after its
            // CREATE TABLE Expenses has already run inside the same transaction
            stmt.execute("CREATE TABLE Categories (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)");
            stmt.execute("CREATE TRIGGER block_seed BEFORE INSERT ON Categories "
                    + "BEGIN SELECT RAISE(ABORT, 'simulated failure'); END");

            assertThrows(SQLException.class, () -> SchemaMigrator.migrate(conn));

            // Neither the Expenses table (created earlier in v1) nor the version bump survived
            assertEquals(0, SchemaMigrator.currentVersion(conn));
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'Expenses'")) {
                assertEquals(0, rs.getInt(1));
            }
        }
    }
}
