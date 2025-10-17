package com.expensetracker;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.sql.ResultSet;
import java.sql.PreparedStatement;

public class DatabaseManager {
	
	public static void addExpense(LocalDate date, String categoryName, double amount, String description) {
	    String sql = "INSERT INTO Expenses(date, category_id, amount, description) VALUES(?, (SELECT id FROM Categories WHERE name = ?), ?, ?)";

	    try (Connection conn = DriverManager.getConnection(URL);
	         PreparedStatement pstmt = conn.prepareStatement(sql)) {

	        pstmt.setString(1, date.toString());
	        pstmt.setString(2, categoryName);
	        pstmt.setDouble(3, amount);
	        pstmt.setString(4, description);
	        pstmt.executeUpdate();

	    } catch (SQLException e) {
	        System.out.println(e.getMessage());
	    }
	}
	// In DatabaseManager.java
	public static List<String> getAllCategoryNames() {
	    String sql = "SELECT name FROM Categories ORDER BY name";
	    List<String> categories = new ArrayList<>();
	    try (Connection conn = DriverManager.getConnection(URL);
	         Statement stmt = conn.createStatement();
	         ResultSet rs = stmt.executeQuery(sql)) {

	        while (rs.next()) {
	            categories.add(rs.getString("name"));
	        }
	    } catch (SQLException e) {
	        System.out.println(e.getMessage());
	    }
	    return categories;
	}
	public static List<Expense> getAllExpenses() {
	    String sql = "SELECT e.id, e.date, c.name as category, e.amount, e.description " +
	                 "FROM Expenses e " +
	                 "JOIN Categories c ON e.category_id = c.id ORDER BY e.date DESC";

	    List<Expense> expenses = new ArrayList<>();

	    try (Connection conn = DriverManager.getConnection(URL);
	         Statement stmt = conn.createStatement();
	         ResultSet rs = stmt.executeQuery(sql)) {

	        while (rs.next()) {
	            int id = rs.getInt("id");
	            LocalDate date = LocalDate.parse(rs.getString("date"));
	            String category = rs.getString("category");
	            double amount = rs.getDouble("amount");
	            String description = rs.getString("description");

	            Expense expense = new Expense(id, date, category, amount, description);
	            expenses.add(expense);
	        }
	    } catch (SQLException e) {
	        System.out.println(e.getMessage());
	    }
	    return expenses;
	}

    private static final String URL = "jdbc:sqlite:expenses.db";

    public static void initializeDatabase() {
    	
    	 try {
    	        // --- ADD THIS LINE ---
    	        Class.forName("org.sqlite.JDBC"); 

    	    } catch (ClassNotFoundException e) {
    	        System.out.println("SQLite JDBC driver not found.");
    	        e.printStackTrace();
    	        return; // Exit if driver is not found
    	    }
    	
    	
        String sqlCategories = "CREATE TABLE IF NOT EXISTS Categories (\n"
                + " id INTEGER PRIMARY KEY AUTOINCREMENT,\n"
                + " name TEXT NOT NULL UNIQUE\n"
                + ");";

        String sqlExpenses = "CREATE TABLE IF NOT EXISTS Expenses (\n"
                + " id INTEGER PRIMARY KEY AUTOINCREMENT,\n"
                + " amount DECIMAL(10, 2) NOT NULL,\n"
                + " date DATE NOT NULL,\n"
                + " description TEXT,\n"
                + " category_id INTEGER,\n"
                + " FOREIGN KEY (category_id) REFERENCES Categories (id)\n"
                + ");";

        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement()) {
            // Create tables
            stmt.execute(sqlCategories);
            stmt.execute(sqlExpenses);
            System.out.println("Database and tables initialized successfully.");

            // Insert default categories if they don't exist
            stmt.execute("INSERT OR IGNORE INTO Categories (name) VALUES ('Food');");
            stmt.execute("INSERT OR IGNORE INTO Categories (name) VALUES ('Transport');");
            stmt.execute("INSERT OR IGNORE INTO Categories (name) VALUES ('Utilities');");
            stmt.execute("INSERT OR IGNORE INTO Categories (name) VALUES ('Entertainment');");
            stmt.execute("INSERT OR IGNORE INTO Categories (name) VALUES ('Other');");

        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }
}