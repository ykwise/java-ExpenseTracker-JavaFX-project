package com.expensetracker;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Currency;
import java.util.List;

/**
 * User settings stored in the database, next to the data they describe: the currency
 * belongs to the expenses, so it travels with the database file if it is copied elsewhere.
 */
public class SettingsRepository {

    static final String DEFAULT_CURRENCY = "KES";

    /** Offered in the Settings dialog; any valid ISO 4217 code is accepted. */
    public static final List<String> COMMON_CURRENCIES =
            List.of("KES", "UGX", "TZS", "RWF", "NGN", "ZAR", "USD", "EUR", "GBP");

    private static final String CURRENCY_KEY = "currency";

    private final Database db;

    public SettingsRepository(Database db) {
        this.db = db;
    }

    public String getCurrency() {
        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement("SELECT value FROM Settings WHERE key = ?")) {
            pstmt.setString(1, CURRENCY_KEY);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getString(1) : DEFAULT_CURRENCY;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load settings", e);
        }
    }

    /** @throws IllegalArgumentException if {@code code} is not an ISO 4217 currency code */
    public void setCurrency(String code) {
        String normalised = code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT);
        Currency.getInstance(normalised); // throws IllegalArgumentException for unknown codes
        String sql = "INSERT INTO Settings (key, value) VALUES (?, ?) "
                + "ON CONFLICT(key) DO UPDATE SET value = excluded.value";
        try (Connection conn = db.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, CURRENCY_KEY);
            pstmt.setString(2, normalised);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not save settings", e);
        }
    }

    public MoneyFormat moneyFormat() {
        return new MoneyFormat(getCurrency());
    }
}
