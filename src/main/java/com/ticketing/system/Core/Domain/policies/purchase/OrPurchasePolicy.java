package com.ticketing.system.Core.Domain.policies.purchase;

import com.ticketing.system.Core.Domain.shared.InvariantChecked;

/**
 * Composite {@link PurchasePolicy} that is satisfied when <em>either</em> of its
 * child policies is satisfied. Failure messages from both children are combined
 * with " OR ".
 */
public class OrPurchasePolicy implements PurchasePolicy, InvariantChecked {

    private final PurchasePolicy leftPolicy;
    private final PurchasePolicy rightPolicy;

    /**
     * @param leftPolicy  the first child policy (must be non-null)
     * @param rightPolicy the second child policy (must be non-null)
     * @throws IllegalStateException if either child is {@code null}
     */
    public OrPurchasePolicy(PurchasePolicy leftPolicy, PurchasePolicy rightPolicy) {
        this.leftPolicy = leftPolicy;
        this.rightPolicy = rightPolicy;
        checkInvariants();
    }

    /**
     * @throws IllegalStateException if either child policy is {@code null}
     */
    @Override
    public void checkInvariants() {
        if (leftPolicy == null || rightPolicy == null) {
            throw new IllegalStateException("OrPurchasePolicy invariant violated: both policies must be non-null");
        }
    }

    /**
     * @param context the purchase being evaluated
     * @return {@code true} if either child policy is satisfied
     */
    @Override
    public boolean isSatisfiedBy(PurchaseContext context) {
        return leftPolicy.isSatisfiedBy(context) || rightPolicy.isSatisfiedBy(context);
    }

    /**
     * @return both children's messages joined with " OR ", skipping any that are
     *         blank
     */
    @Override
    public String getFailureMessage() {
        String left = leftPolicy.getFailureMessage();
        String right = rightPolicy.getFailureMessage();

        if (left == null || left.isBlank()) {
            return right == null ? "" : right;
        }
        if (right == null || right.isBlank()) {
            return left;
        }

        return left + " OR " + right;
    }

    /** @return the first child policy */
    public PurchasePolicy getLeftPolicy()  { return leftPolicy; }

    /** @return the second child policy */
    public PurchasePolicy getRightPolicy() { return rightPolicy; }
}
