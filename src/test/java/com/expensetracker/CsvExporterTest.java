package com.expensetracker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvExporterTest {

    @Test
    void plainValues_areNotQuoted() {
        assertEquals("Lunch", CsvExporter.escape("Lunch"));
    }

    @Test
    void commas_areQuotedNotReplaced() {
        assertEquals("\"Rice, beans\"", CsvExporter.escape("Rice, beans"));
    }

    @Test
    void quotes_areDoubled() {
        assertEquals("\"The \"\"OG\"\" movie\"", CsvExporter.escape("The \"OG\" movie"));
    }

    @Test
    void nullDescription_becomesEmpty() {
        assertEquals("", CsvExporter.escape(null));
    }

    @Test
    void write_producesHeaderAndOneRowPerExpense() throws IOException {
        StringWriter out = new StringWriter();
        CsvExporter.write(List.of(
                new Expense(1, LocalDate.of(2026, 11, 15), "Food", 150.5, "Rice, beans")), out);

        assertEquals("Date,Category,Amount,Description\r\n"
                + "2026-11-15,Food,150.50,\"Rice, beans\"\r\n", out.toString());
    }

    // BUG-01: large amounts came out as 1.25E7
    @Test
    void amounts_arePlainDecimalsWithTwoPlaces() {
        assertEquals("12500000.00", CsvExporter.amount(12_500_000));
        assertEquals("50.00", CsvExporter.amount(50));
        assertEquals("0.10", CsvExporter.amount(0.1));
        assertEquals("1234.57", CsvExporter.amount(1234.567));
    }

    // BUG-02: CSV injection
    @Test
    void formulaLikeText_isPrefixedWithApostrophe() {
        assertEquals("'=HYPERLINK(\"http://x\")", CsvExporter.neutraliseFormula("=HYPERLINK(\"http://x\")"));
        assertEquals("'+1+1", CsvExporter.neutraliseFormula("+1+1"));
        assertEquals("'-2+3", CsvExporter.neutraliseFormula("-2+3"));
        assertEquals("'@SUM(A1)", CsvExporter.neutraliseFormula("@SUM(A1)"));
        assertEquals("'\tcmd", CsvExporter.neutraliseFormula("\tcmd"));
    }

    @Test
    void ordinaryText_isUnchanged() {
        assertEquals("Lunch = 2 plates", CsvExporter.neutraliseFormula("Lunch = 2 plates"));
        assertEquals("", CsvExporter.neutraliseFormula(""));
        assertEquals(null, CsvExporter.neutraliseFormula(null));
    }

    @Test
    void write_neutralisesFormulasAndStillQuotesProperly() throws IOException {
        StringWriter out = new StringWriter();
        CsvExporter.write(List.of(new Expense(1, LocalDate.of(2026, 11, 15), "Other", 12_500_000,
                "=HYPERLINK(\"http://x\",\"click\")")), out);

        assertEquals("Date,Category,Amount,Description\r\n"
                + "2026-11-15,Other,12500000.00,\"'=HYPERLINK(\"\"http://x\"\",\"\"click\"\")\"\r\n",
                out.toString());
    }
}
