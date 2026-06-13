package com.ticketing.system.Core.Domain.policies.purchase;

public class MaxTicketsPurchasePolicy implements PurchasePolicy {

    private final int maximumTickets;

    public MaxTicketsPurchasePolicy(int maximumTickets) {
        if (maximumTickets < 0) {
            throw new IllegalArgumentException("Maximum tickets cannot be negative");
        }

        this.maximumTickets = maximumTickets;
    }

    @Override
    public boolean isSatisfiedBy(PurchaseContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Purchase context cannot be null");
        }

        return context.getQuantity() <= maximumTickets;
    }

    @Override
    public String getFailureMessage() {
        return "You can buy at most " + maximumTickets + " tickets";
    }
    public int getMaximumTickets() { return maximumTickets; }
}