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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for Database, ExpenseRepository and BudgetRepository:
 * a real SQLite database in a temporary file, fresh for each test.
 * Months are passed explicitly (15 Nov 2026 is "today"), so results are the same on any day.
 */
class RepositoryIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final YearMonth THIS_MONTH = YearMonth.from(TODAY);

    @TempDir
    Path tempDir;

    private String url;
    private ExpenseRepository repo;
    private BudgetRepository budgets;
    private SettingsRepository settings;

    @BeforeEach
    void setUp() {
        url = "jdbc:sqlite:" + tempDir.resolve("test.db");
        Database db = new Database(url);
        db.initialize();
        repo = new ExpenseRepository(db);
        budgets = new BudgetRepository(db);
        settings = new SettingsRepository(db);
    }

    // ---------------------------------------------------------------- schema

    @Test
    void initialize_createsDefaultCategories() {
        assertEquals(List.of("Entertainment", "Food", "Other", "Transport", "Utilities"),
                repo.getAllCategoryNames());
    }

    @Test
    void initialize_isSafeToRunTwice() {
        new Database(url).initialize();
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

        assertEquals(Map.of("Food", 150.0, "Transport", 20.0), repo.getMonthlyCategorySummary(THIS_MONTH));
    }

    @Test
    void getExpenses_returnsOnlyTheRequestedMonth() {
        repo.addExpense(TODAY, "Food", 100, "November");
        repo.addExpense(LocalDate.of(2026, 10, 31), "Food", 200, "October");
        repo.addExpense(LocalDate.of(2026, 10, 1), "Food", 300, "October too");

        assertEquals(List.of("November"),
                repo.getExpenses(THIS_MONTH).stream().map(Expense::getDescription).toList());
        assertEquals(List.of("October", "October too"),
                repo.getExpenses(YearMonth.of(2026, 10)).stream().map(Expense::getDescription).toList());
        assertEquals(3, repo.getAllExpenses().size());
    }

    @Test
    void pastMonth_summaryAndBudgetsUseThatMonthsSpending() {
        budgets.setBudget("Food", 1000);
        repo.addExpense(LocalDate.of(2026, 10, 10), "Food", 900, "");
        repo.addExpense(TODAY, "Food", 100, "");

        YearMonth october = YearMonth.of(2026, 10);
        assertEquals(Map.of("Food", 900.0), repo.getMonthlyCategorySummary(october));
        assertEquals(List.of(new BudgetSummary("Food", 1000, 900)), budgets.getBudgetSummaries(october));
        assertEquals(List.of(new BudgetSummary("Food", 1000, 100)), budgets.getBudgetSummaries(THIS_MONTH));
    }

    @Test
    void yearBoundary_decemberToJanuary() {
        repo.addExpense(LocalDate.of(2026, 12, 31), "Food", 10, "");
        repo.addExpense(LocalDate.of(2027, 1, 1), "Food", 20, "");
        assertEquals(Map.of("Food", 10.0), repo.getMonthlyCategorySummary(YearMonth.of(2026, 12)));
        assertEquals(Map.of("Food", 20.0), repo.getMonthlyCategorySummary(YearMonth.of(2027, 1)));
    }

    // --------------------------------------------------------------- budgets

    @Test
    void getBudget_isEmptyWhenNotSet() {
        assertFalse(budgets.getBudget("Food").isPresent());
    }

    @Test
    void setBudget_isIdempotentUpsert() {
        budgets.setBudget("Food", 5000);
        budgets.setBudget("Food", 5000);
        budgets.setBudget("Food", 6000);

        assertEquals(OptionalDouble.of(6000), budgets.getBudget("Food"));
        assertEquals(1, budgets.getBudgetSummaries(THIS_MONTH).size());
    }

    @Test
    void setBudget_rejectsZeroOrNegative() {
        assertThrows(IllegalArgumentException.class, () -> budgets.setBudget("Food", 0));
        assertThrows(IllegalArgumentException.class, () -> budgets.setBudget("Food", -5));
    }

    @Test
    void getMonthlySpent_sumsOnlyThisCategoryThisMonth() {
        repo.addExpense(TODAY, "Food", 300, "");
        repo.addExpense(TODAY, "Food", 200, "");
        repo.addExpense(TODAY, "Transport", 1000, "");
        repo.addExpense(TODAY.minusMonths(1), "Food", 1000, "");

        assertEquals(500, budgets.getMonthlySpent("Food", THIS_MONTH));
        assertEquals(0, budgets.getMonthlySpent("Utilities", THIS_MONTH));
    }

    @Test
    void budgetSummaries_includeUnspentBudgetsAndSkipUnbudgetedCategories() {
        budgets.setBudget("Food", 1000);
        budgets.setBudget("Transport", 500);
        repo.addExpense(TODAY, "Food", 850, "");
        repo.addExpense(TODAY, "Other", 50, ""); // no budget: not listed

        assertEquals(List.of(
                new BudgetSummary("Food", 1000, 850),
                new BudgetSummary("Transport", 500, 0)), budgets.getBudgetSummaries(THIS_MONTH));
    }

    // -------------------------------------------------------------- settings

    @Test
    void currency_defaultsToKes() {
        assertEquals("KES", settings.getCurrency());
        assertEquals("KES 10.00", settings.moneyFormat().format(10));
    }

    @Test
    void setCurrency_normalisesAndPersists() {
        settings.setCurrency(" usd ");
        assertEquals("USD", settings.getCurrency());
    }

    @Test
    void setCurrency_rejectsUnknownCodes() {
        assertThrows(IllegalArgumentException.class, () -> settings.setCurrency("XYZ1"));
        assertEquals("KES", settings.getCurrency());
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
