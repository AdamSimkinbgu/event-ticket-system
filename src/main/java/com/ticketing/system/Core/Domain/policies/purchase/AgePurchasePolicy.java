package com.ticketing.system.Core.Domain.policies.purchase;

import com.ticketing.system.Core.Domain.shared.InvariantChecked;

public class AgePurchasePolicy implements PurchasePolicy, InvariantChecked {

    private final int minimumAge;

    public AgePurchasePolicy(int minimumAge) {
        this.minimumAge = minimumAge;
        checkInvariants();
    }

    @Override
    public void checkInvariants() {
        if (minimumAge < 0) {
            throw new IllegalStateException("AgePurchasePolicy invariant violated: minimumAge cannot be negative (was " + minimumAge + ")");
        }
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
    public int getMinimumAge() { return minimumAge; }
}
