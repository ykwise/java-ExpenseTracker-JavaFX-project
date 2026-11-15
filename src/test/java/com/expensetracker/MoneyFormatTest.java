package com.expensetracker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class MoneyFormatTest {

    private final MoneyFormat kes = new MoneyFormat("KES");

    @Test
    void formatsWithCodeThousandsSeparatorAndTwoDecimals() {
        assertEquals("KES 1,250.00", kes.format(1250));
        assertEquals("KES 0.50", kes.format(0.5));
        assertEquals("KES 1,234,567.89", kes.format(1234567.891));
    }

    @Test
    void numberOmitsCurrency() {
        assertEquals("150.50", kes.number(150.5));
    }

    @Test
    void isTheSameOnEveryMachineLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY); // would print 1.250,00
            assertEquals("KES 1,250.00", kes.format(1250));
        } finally {
            Locale.setDefault(original);
        }
    }
}
