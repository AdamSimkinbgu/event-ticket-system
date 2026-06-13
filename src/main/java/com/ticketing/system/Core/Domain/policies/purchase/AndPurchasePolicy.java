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
        return leftPolicy.getFailureMessage() + " AND " + rightPolicy.getFailureMessage();
    }
    public PurchasePolicy getLeftPolicy()  { return leftPolicy; }
public PurchasePolicy getRightPolicy() { return rightPolicy; }
}