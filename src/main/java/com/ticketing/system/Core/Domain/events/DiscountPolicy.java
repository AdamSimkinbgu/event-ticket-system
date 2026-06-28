package com.ticketing.system.Core.Domain.events;

import java.time.LocalDateTime;

import com.ticketing.system.Core.Domain.shared.InvariantChecked;

/**
 * Value object describing a flat percentage discount applied to ticket prices.
 *
 * <p>Note: this type is not part of the current implementation plan and is not
 * yet wired into the checkout pricing path — it is retained for the discount
 * feature still on the backlog.
 */
public class DiscountPolicy implements InvariantChecked {

    /** Discount percentage in the range 0–100. */
    private final double discountPercentage;

    /**
     * @param discountPercentage the discount percentage, between 0 and 100
     * @throws IllegalStateException if the percentage is outside 0–100
     */
    public DiscountPolicy(double discountPercentage) {
        this.discountPercentage = discountPercentage;
        checkInvariants();
    }

    /**
     * @throws IllegalStateException if the percentage is outside 0–100
     */
    @Override
    public void checkInvariants() {
        if (discountPercentage < 0 || discountPercentage > 100) {
            throw new IllegalStateException(
                    "DiscountPolicy invariant violated: discountPercentage must be between 0 and 100 (was " + discountPercentage + ")");
        }
    }

    /** @return the discount percentage (0–100) */
    public double getDiscountPercentage() {
        return discountPercentage;
    }

    /**
     * Applies the discount to a single price.
     *
     * @param price the pre-discount price
     * @return the discounted price
     */
    public double apply(double price) {
        return price * (1 - discountPercentage / 100);
    }

    /**
     * @param basePricePerTicket the pre-discount price of one ticket
     * @param quantity           the number of tickets
     * @return the discounted total for {@code quantity} tickets
     */
    public double calculateFinalPrice(double basePricePerTicket, int quantity) {
        return apply(basePricePerTicket) * quantity;
    }

    /**
     * Computes the discounted total for a quantity of tickets.
     *
     * @param quantity                    the number of tickets (must be positive)
     * @param priceAtOneTicketReservation the per-ticket base price (must be non-negative)
     * @param now                         the evaluation time (reserved for time-bound discounts)
     * @return the discounted total price
     * @throws IllegalArgumentException if the quantity is not positive or the price is null/negative
     */
    public double calculate(int quantity, Double priceAtOneTicketReservation, LocalDateTime now) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (priceAtOneTicketReservation == null || priceAtOneTicketReservation < 0) {
            throw new IllegalArgumentException("Price must be non-negative");
        }

        double base = quantity * priceAtOneTicketReservation;
        return apply(base);
    }

    /**
     * Computes the discounted per-ticket price.
     *
     * @param quantity                    the number of tickets (must be positive)
     * @param priceAtOneTicketReservation the per-ticket base price (must be non-negative)
     * @param now                         the evaluation time (reserved for time-bound discounts)
     * @return the discounted price per ticket
     * @throws IllegalArgumentException if the quantity is not positive or the price is null/negative
     */
    public double calculatePriceforoneticket(int quantity, Double priceAtOneTicketReservation, LocalDateTime now) {
        return calculate(quantity, priceAtOneTicketReservation, now) / quantity;
    }

}
