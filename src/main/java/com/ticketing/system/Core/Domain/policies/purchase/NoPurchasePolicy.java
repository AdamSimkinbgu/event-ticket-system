package com.ticketing.system.Core.Domain.policies.purchase;

/**
 * Null-object {@link PurchasePolicy} that permits every purchase. Used as the
 * default when an Event or company has no purchase restrictions configured, so
 * callers never have to null-check the policy.
 */
public class NoPurchasePolicy implements PurchasePolicy {

    /**
     * @param context ignored
     * @return always {@code true}
     */
    @Override
    public boolean isSatisfiedBy(PurchaseContext context) {
        return true;
    }

    /**
     * @return an empty string — there is no restriction to explain
     */
    @Override
    public String getFailureMessage() {
        return "";
    }
}
