package com.ticketing.system.Core.Domain.policies.purchase;

public class AgePurchasePolicy implements PurchasePolicy {

    private final int minimumAge;

    public AgePurchasePolicy(int minimumAge) {
        if (minimumAge < 0) {
            throw new IllegalArgumentException("Minimum age cannot be negative");
        }

        this.minimumAge = minimumAge;
    }

    @Override
    public boolean isSatisfiedBy(PurchaseContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Purchase context cannot be null");
        }

        Integer buyerAge = context.getBuyerAge();

        if (buyerAge == null) {
            return false;
        }

        return buyerAge >= minimumAge;
    }

    @Override
    public String getFailureMessage() {
        return "You must be at least " + minimumAge + " years old to buy tickets";
    }
}