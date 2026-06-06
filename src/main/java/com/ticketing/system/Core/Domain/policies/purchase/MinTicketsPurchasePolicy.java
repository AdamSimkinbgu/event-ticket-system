package com.ticketing.system.Core.Domain.policies.purchase;

public class MinTicketsPurchasePolicy implements PurchasePolicy {

    private final int minimumTickets;

    public MinTicketsPurchasePolicy(int minimumTickets) {
        if (minimumTickets < 0) {
            throw new IllegalArgumentException("Minimum tickets cannot be negative");
        }

        this.minimumTickets = minimumTickets;
    }

    @Override
    public boolean isSatisfiedBy(PurchaseContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Purchase context cannot be null");
        }

        return context.getQuantity() >= minimumTickets;
    }

    @Override
    public String getFailureMessage() {
        return "You must buy at least " + minimumTickets + " tickets";
    }
}