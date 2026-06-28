package com.ticketing.system.Presentation.components;


/**
 * Small presentation-layer money helper: converts dollar amounts to integer cents
 * and formats cent amounts as a "$X.YY" string. Non-instantiable utility.
 */
public final class Money {

    private Money() { }

    /**
     * @param dollars a dollar amount
     * @return the amount in integer cents, rounded to the nearest cent
     */
    public static long toCents(double dollars) {
        return Math.round(dollars * 100);
    }

    /**
     * @param cents an amount in integer cents (may be negative)
     * @return the amount formatted as "$X.YY" (with a leading "-" when negative)
     */
    public static String format(long cents) {
        long abs = Math.abs(cents);
        return (cents < 0 ? "-" : "") + "$" + (abs / 100) + "." + String.format("%02d", abs % 100);
    }
}