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
                + "2026-11-15,Food,150.5,\"Rice, beans\"\r\n", out.toString());
    }
}
