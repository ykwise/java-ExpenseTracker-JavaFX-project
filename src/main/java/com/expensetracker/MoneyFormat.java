package com.expensetracker;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formats amounts for display, e.g. {@code KES 1,250.00}.
 *
 * Uses a fixed, locale-independent pattern so the app shows the same thing on every
 * machine (a German locale would otherwise print 1.250,00). The currency is shown as
 * its ISO code rather than a symbol, because many currencies share "$" or have no
 * widely supported symbol (KES, UGX, TZS).
 */
public final class MoneyFormat {

    private final String currencyCode;

    public MoneyFormat(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String currencyCode() {
        return currencyCode;
    }

    /** {@code KES 1,250.00} */
    public String format(double amount) {
        return currencyCode + " " + number(amount);
    }

    /** {@code 1,250.00} without the currency, for table cells under a "KES" header. */
    public String number(double amount) {
        // DecimalFormat is not thread-safe, so create one per call; this is UI-thread only and cheap
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(amount);
    }
}
