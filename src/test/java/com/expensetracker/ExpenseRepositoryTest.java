package com.expensetracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests: a real SQLite database in a temporary file, fresh for each test.
 * "Today" is pinned to 15 Nov 2026 so month-based queries give the same answer on any day.
 */
class ExpenseRepositoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final Clock CLOCK =
            Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    private String url;
    private ExpenseRepository repo;

    @BeforeEach
    void setUp() {
        url = "jdbc:sqlite:" + tempDir.resolve("test.db");
        repo = new ExpenseRepository(url, CLOCK);
        repo.initialize();
    }

    // ---------------------------------------------------------------- schema

    @Test
    void initialize_createsDefaultCategories() {
        assertEquals(List.of("Entertainment", "Food", "Other", "Transport", "Utilities"),
                repo.getAllCategoryNames());
    }

    @Test
    void initialize_isSafeToRunTwice() {
        repo.initialize();
        assertEquals(5, repo.getAllCategoryNames().size());
    }

    // -------------------------------------------------------------- expenses

    @Test
    void addExpense_thenGetAll_returnsItNewestFirst() {
        repo.addExpense(TODAY.minusDays(3), "Food", 100, "Groceries");
        repo.addExpense(TODAY, "Transport", 50, "Matatu");

        List<Expense> all = repo.getAllExpenses();
        assertEquals(2, all.size());
        assertEquals("Matatu", all.get(0).getDescription());
        assertEquals("Groceries", all.get(1).getDescription());
    }

    @Test
    void addExpense_unknownCategory_failsInsteadOfStoringNull() {
        assertThrows(IllegalArgumentException.class,
                () -> repo.addExpense(TODAY, "NoSuchCategory", 10, "x"));
        assertTrue(repo.getAllExpenses().isEmpty());
    }

    @Test
    void updateExpense_changesEveryField() {
        repo.addExpense(TODAY, "Food", 100, "Lunch");
        int id = repo.getAllExpenses().get(0).getId();

        repo.updateExpense(id, TODAY.minusDays(1), "Entertainment", 250, "Cinema");

        Expense e = repo.getAllExpenses().get(0);
        assertEquals(TODAY.minusDays(1), e.getDate());
        assertEquals("Entertainment", e.getCategory());
        assertEquals(250, e.getAmount());
        assertEquals("Cinema", e.getDescription());
    }

    @Test
    void deleteExpense_removesIt() {
        repo.addExpense(TODAY, "Food", 100, "Lunch");
        repo.deleteExpense(repo.getAllExpenses().get(0).getId());
        assertTrue(repo.getAllExpenses().isEmpty());
    }

    @Test
    void monthlySummary_onlyCountsCurrentMonth() {
        repo.addExpense(TODAY, "Food", 100, "");
        repo.addExpense(TODAY.withDayOfMonth(1), "Food", 50, "");      // first day: included
        repo.addExpense(TODAY.withDayOfMonth(30), "Transport", 20, ""); // last day: included
        repo.addExpense(LocalDate.of(2026, 10, 31), "Food", 999, "");  // last month: excluded
        repo.addExpense(LocalDate.of(2026, 12, 1), "Food", 999, "");   // next month: excluded

        assertEquals(Map.of("Food", 150.0, "Transport", 20.0), repo.getMonthlyCategorySummary());
    }

    // --------------------------------------------------------------- budgets

    @Test
    void getBudget_isEmptyWhenNotSet() {
        assertFalse(repo.getBudget("Food").isPresent());
    }

    @Test
    void setBudget_isIdempotentUpsert() {
        repo.setBudget("Food", 5000);
        repo.setBudget("Food", 5000);
        repo.setBudget("Food", 6000);

        assertEquals(OptionalDouble.of(6000), repo.getBudget("Food"));
        assertEquals(1, repo.getBudgetSummaries().size());
    }

    @Test
    void setBudget_rejectsZeroOrNegative() {
        assertThrows(IllegalArgumentException.class, () -> repo.setBudget("Food", 0));
        assertThrows(IllegalArgumentException.class, () -> repo.setBudget("Food", -5));
    }

    @Test
    void getMonthlySpent_sumsOnlyThisCategoryThisMonth() {
        repo.addExpense(TODAY, "Food", 300, "");
        repo.addExpense(TODAY, "Food", 200, "");
        repo.addExpense(TODAY, "Transport", 1000, "");
        repo.addExpense(TODAY.minusMonths(1), "Food", 1000, "");

        assertEquals(500, repo.getMonthlySpent("Food"));
        assertEquals(0, repo.getMonthlySpent("Utilities"));
    }

    @Test
    void budgetSummaries_includeUnspentBudgetsAndSkipUnbudgetedCategories() {
        repo.setBudget("Food", 1000);
        repo.setBudget("Transport", 500);
        repo.addExpense(TODAY, "Food", 850, "");
        repo.addExpense(TODAY, "Other", 50, ""); // no budget: not listed

        assertEquals(List.of(
                new BudgetSummary("Food", 1000, 850),
                new BudgetSummary("Transport", 500, 0)), repo.getBudgetSummaries());
    }

    // ----------------------------------------------------------- constraints

    @Test
    void foreignKeys_areEnforced() throws SQLException {
        // Raw insert with a category id that doesn't exist must be rejected by SQLite itself
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
            assertThrows(SQLException.class, () -> stmt.execute(
                    "INSERT INTO Expenses (amount, date, category_id) VALUES (1, '2026-11-15', 9999)"));
        }
    }

    @Test
    void categoryNames_areUnique() throws SQLException {
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            assertThrows(SQLException.class,
                    () -> stmt.execute("INSERT INTO Categories (name) VALUES ('Food')"));
        }
    }

    // ----------------------------------------------------------- performance

    /**
     * Guards the query optimisation: the month filter must use an index, not scan the table.
     * SQLite's equivalent of EXPLAIN ANALYZE is EXPLAIN QUERY PLAN.
     */
    @Test
    void monthlySpendQuery_usesIndexNotFullScan() throws SQLException {
        String plan = queryPlan("SELECT COALESCE(SUM(e.amount), 0) FROM Expenses e "
                + "JOIN Categories c ON e.category_id = c.id "
                + "WHERE c.name = 'Food' AND e.date >= '2026-11-01' AND e.date < '2026-12-01'");

        assertTrue(plan.contains("idx_expenses_category_date"), plan);
        assertFalse(plan.contains("SCAN e"), plan);
    }

    @Test
    void oldStrftimeFilter_wouldHaveScannedTheWholeTable() throws SQLException {
        // Documents why the query was rewritten: wrapping the column in a function defeats the index
        String plan = queryPlan("SELECT SUM(e.amount) FROM Expenses e "
                + "WHERE strftime('%Y-%m', e.date) = '2026-11'");

        assertTrue(plan.contains("SCAN e"), plan);
    }

    private String queryPlan(String sql) throws SQLException {
        StringBuilder plan = new StringBuilder();
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("EXPLAIN QUERY PLAN " + sql)) {
            while (rs.next()) {
                plan.append(rs.getString("detail")).append('\n');
            }
        }
        return plan.toString();
    }
}
