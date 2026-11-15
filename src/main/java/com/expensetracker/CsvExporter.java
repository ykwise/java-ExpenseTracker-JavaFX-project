package com.expensetracker;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Writes expenses as RFC 4180 CSV.
 *
 * The original export replaced commas in descriptions with semicolons, which
 * silently changed the user's data. Proper quoting keeps the text intact and
 * opens correctly in Excel, Numbers and Google Sheets.
 *
 * Two further safeguards:
 * - Amounts are written as plain decimals with two places ({@code 12500000.00}), never in
 *   Java's scientific notation ({@code 1.25E7}), which spreadsheets may misread.
 * - Text that a spreadsheet would treat as a formula (starting with =, +, -, @, tab or
 *   carriage return) is prefixed with an apostrophe, following OWASP's guidance on CSV
 *   injection. Otherwise a description like {@code =HYPERLINK(...)} runs when the file is opened.
 */
public final class CsvExporter {

    static final String HEADER = "Date,Category,Amount,Description";

    private CsvExporter() {
    }

    public static void write(List<Expense> expenses, Writer out) throws IOException {
        out.write(HEADER);
        out.write("\r\n");
        for (Expense expense : expenses) {
            out.write(String.join(",",
                    expense.getDate().toString(),
                    escape(neutraliseFormula(expense.getCategory())),
                    amount(expense.getAmount()),
                    escape(neutraliseFormula(expense.getDescription()))));
            out.write("\r\n");
        }
    }

    /** {@code 12500000} -> {@code 12500000.00}; {@code 50} -> {@code 50.00}. */
    static String amount(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Prefixes text that would start a spreadsheet formula with an apostrophe. */
    static String neutraliseFormula(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        boolean formulaStart = first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r';
        return formulaStart ? "'" + value : value;
    }

    /** Quotes a field if it contains a comma, quote or line break; doubles any embedded quotes. */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
