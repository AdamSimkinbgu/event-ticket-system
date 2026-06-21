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

        // The minimum is only enforced at checkout — at reserve the cart is still being built up.
        if (context.getStage() == PurchaseStage.RESERVE) {
            return true;
        }

        return context.getQuantity() >= minimumTickets;
    }

    @Override
    public String getFailureMessage() {
        return "You must buy at least " + minimumTickets + " tickets";
        
    }
    public int getMinimumTickets() { return minimumTickets; }
}
