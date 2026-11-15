package com.expensetracker;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists, adds, renames and deletes categories.
 *
 * Integrity is enforced by the database, not only by checks in this class:
 * - a unique index on name (case-insensitive) rejects duplicates, so there is no
 *   window between "check it doesn't exist" and "insert it" for a duplicate to slip in;
 * - foreign keys stop a category that expenses still use from being deleted.
 * Expenses and budgets reference categories by id, so a rename updates them all at once.
 */
public class CategoryRepository {

    static final int MAX_NAME_LENGTH = 40;

    // SQLite's extended result code for a UNIQUE violation (SQLITE_CONSTRAINT_UNIQUE)
    private static final int SQLITE_CONSTRAINT_UNIQUE = 2067;

    private final Database db;

    public CategoryRepository(Database db) {
        this.db = db;
    }

    public List<String> getAllNames() {
        List<String> names = new ArrayList<>();
        try (Connection conn = db.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM Categories ORDER BY name COLLATE NOCASE")) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load categories", e);
        }
        return names;
    }

    /** @throws IllegalArgumentException if the name is blank, too long or already exists */
    public void add(String name) {
        String clean = validName(name);
        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement("INSERT INTO Categories (name) VALUES (?)")) {
            pstmt.setString(1, clean);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw translate(e, clean, "Could not add the category");
        }
    }

    /** @throws IllegalArgumentException if the new name is invalid or taken, or the old one doesn't exist */
    public void rename(String oldName, String newName) {
        String clean = validName(newName);
        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement("UPDATE Categories SET name = ? WHERE id = ?")) {
            pstmt.setString(1, clean);
            pstmt.setInt(2, db.categoryId(conn, oldName));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw translate(e, clean, "Could not rename the category");
        }
    }

    /**
     * Deletes a category that no expense uses, together with its budget if it has one.
     *
     * Runs as one transaction: the usage check, the budget delete and the category delete
     * either all happen or none do, so a failure can't leave a budget pointing at nothing
     * or remove the budget but keep the category.
     *
     * @throws IllegalArgumentException if expenses still use the category
     */
    public void delete(String name) {
        try (Connection conn = db.connect()) {
            conn.setAutoCommit(false);
            try {
                int id = db.categoryId(conn, name);
                int used = countExpenses(conn, id);
                if (used > 0) {
                    throw new IllegalArgumentException(String.format(
                            "\"%s\" is used by %d expense%s. Move or delete %s first.",
                            name, used, used == 1 ? "" : "s", used == 1 ? "it" : "them"));
                }
                try (PreparedStatement budget = conn.prepareStatement("DELETE FROM Budgets WHERE category_id = ?");
                     PreparedStatement category = conn.prepareStatement("DELETE FROM Categories WHERE id = ?")) {
                    budget.setInt(1, id);
                    budget.executeUpdate();
                    category.setInt(1, id);
                    category.executeUpdate();
                }
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the category", e);
        }
    }

    /** Number of expenses in the category (any month). */
    public int countExpenses(String name) {
        try (Connection conn = db.connect()) {
            return countExpenses(conn, db.categoryId(conn, name));
        } catch (SQLException e) {
            throw new DataAccessException("Could not count expenses", e);
        }
    }

    private static int countExpenses(Connection conn, int categoryId) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM Expenses WHERE category_id = ?")) {
            pstmt.setInt(1, categoryId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    static String validName(String name) {
        String clean = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("Category name can't be empty.");
        }
        if (clean.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Category name can be at most " + MAX_NAME_LENGTH + " characters.");
        }
        return clean;
    }

    private static RuntimeException translate(SQLException e, String name, String message) {
        if (e.getErrorCode() == SQLITE_CONSTRAINT_UNIQUE
                || (e.getMessage() != null && e.getMessage().contains("UNIQUE constraint failed"))) {
            return new IllegalArgumentException("A category called \"" + name + "\" already exists.");
        }
        return new DataAccessException(message, e);
    }
}
