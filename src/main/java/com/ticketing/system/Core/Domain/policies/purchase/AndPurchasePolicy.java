package com.ticketing.system.Core.Domain.policies.purchase;

public class AndPurchasePolicy implements PurchasePolicy {

    private final PurchasePolicy leftPolicy;
    private final PurchasePolicy rightPolicy;

    public AndPurchasePolicy(PurchasePolicy leftPolicy, PurchasePolicy rightPolicy) {
        if (leftPolicy == null || rightPolicy == null) {
            throw new IllegalArgumentException("Purchase policies cannot be null");
        }

        this.leftPolicy = leftPolicy;
        this.rightPolicy = rightPolicy;
    }

    @Override
    public boolean isSatisfiedBy(PurchaseContext context) {
        return leftPolicy.isSatisfiedBy(context) && rightPolicy.isSatisfiedBy(context);
    }

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

        return left + " AND " + right;
    }
    public PurchasePolicy getLeftPolicy()  { return leftPolicy; }
    public PurchasePolicy getRightPolicy() { return rightPolicy; }
}
