package com.ticketing.system.Core.Domain.policies.purchase;

import com.ticketing.system.Core.Domain.shared.InvariantChecked;

/**
 * {@link PurchasePolicy} leaf that requires the buyer to meet a minimum age.
 *
 * <p>When the buyer's age is unknown (e.g. a guest at reserve time) the check is
 * deferred to checkout, where the age is collected; an unknown age at checkout
 * still fails.
 */
public class AgePurchasePolicy implements PurchasePolicy, InvariantChecked {

    private final int minimumAge;

    /**
     * @param minimumAge the minimum buyer age, in years (must be non-negative)
     * @throws IllegalStateException if {@code minimumAge} is negative
     */
    public AgePurchasePolicy(int minimumAge) {
        this.minimumAge = minimumAge;
        checkInvariants();
    }

    /**
     * @throws IllegalStateException if {@code minimumAge} is negative
     */
    @Override
    public void checkInvariants() {
        if (minimumAge < 0) {
            throw new IllegalStateException("AgePurchasePolicy invariant violated: minimumAge cannot be negative (was " + minimumAge + ")");
        }
    }

    /**
     * @param context the purchase being evaluated
     * @return {@code true} if the buyer is old enough, or the age is unknown and
     *         the stage is {@link PurchaseStage#RESERVE} (deferred to checkout)
     * @throws IllegalArgumentException if {@code context} is {@code null}
     */
    @Override
    public boolean isSatisfiedBy(PurchaseContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Purchase context cannot be null");
        }

        Integer buyerAge = context.getBuyerAge();

        if (buyerAge == null) {
            // Age unknown (e.g. a guest at reserve time) — defer the check to checkout,
            // where the age is collected; an unknown age at checkout still fails.
            return context.getStage() == PurchaseStage.RESERVE;
        }

        return buyerAge >= minimumAge;
    }

    /**
     * @return a message stating the required minimum age
     */
    @Override
    public String getFailureMessage() {
        return "You must be at least " + minimumAge + " years old to buy tickets";
    }

    /** @return the configured minimum age, in years */
    public int getMinimumAge() { return minimumAge; }
}
