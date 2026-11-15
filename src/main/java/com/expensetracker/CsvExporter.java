package com.expensetracker;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Writes expenses as RFC 4180 CSV.
 *
 * The original export replaced commas in descriptions with semicolons, which
 * silently changed the user's data. Proper quoting keeps the text intact and
 * opens correctly in Excel, Numbers and Google Sheets.
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
                    escape(expense.getCategory()),
                    String.valueOf(expense.getAmount()),
                    escape(expense.getDescription())));
            out.write("\r\n");
        }
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
