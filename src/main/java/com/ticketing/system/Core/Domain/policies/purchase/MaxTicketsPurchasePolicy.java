package com.ticketing.system.Core.Domain.policies.purchase;

import com.ticketing.system.Core.Domain.shared.InvariantChecked;

/**
 * {@link PurchasePolicy} leaf that caps the number of tickets a buyer may
 * purchase. Enforced at both reserve and checkout.
 */
public class MaxTicketsPurchasePolicy implements PurchasePolicy, InvariantChecked {

    private final int maximumTickets;

    /**
     * @param maximumTickets the maximum allowed quantity (must be non-negative)
     * @throws IllegalStateException if {@code maximumTickets} is negative
     */
    public MaxTicketsPurchasePolicy(int maximumTickets) {
        this.maximumTickets = maximumTickets;
        checkInvariants();
    }

    /**
     * @throws IllegalStateException if {@code maximumTickets} is negative
     */
    @Override
    public void checkInvariants() {
        if (maximumTickets < 0) {
            throw new IllegalStateException("MaxTicketsPurchasePolicy invariant violated: maximumTickets cannot be negative (was " + maximumTickets + ")");
        }
    }

    /**
     * @param context the purchase being evaluated
     * @return {@code true} if the requested quantity is within the cap
     * @throws IllegalArgumentException if {@code context} is {@code null}
     */
    @Override
    public boolean isSatisfiedBy(PurchaseContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Purchase context cannot be null");
        }

        return context.getQuantity() <= maximumTickets;
    }

    /**
     * @return a message stating the maximum allowed quantity
     */
    @Override
    public String getFailureMessage() {
        return "You can buy at most " + maximumTickets + " tickets";
    }

    /** @return the configured maximum ticket quantity */
    public int getMaximumTickets() { return maximumTickets; }
}
