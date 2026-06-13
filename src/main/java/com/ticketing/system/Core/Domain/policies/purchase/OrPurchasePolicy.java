package com.ticketing.system.Core.Domain.policies.purchase;

public class OrPurchasePolicy implements PurchasePolicy {

    private final PurchasePolicy leftPolicy;
    private final PurchasePolicy rightPolicy;

    public OrPurchasePolicy(PurchasePolicy leftPolicy, PurchasePolicy rightPolicy) {
        if (leftPolicy == null || rightPolicy == null) {
            throw new IllegalArgumentException("Purchase policies cannot be null");
        }

        this.leftPolicy = leftPolicy;
        this.rightPolicy = rightPolicy;
    }

    @Override
    public boolean isSatisfiedBy(PurchaseContext context) {
        return leftPolicy.isSatisfiedBy(context) || rightPolicy.isSatisfiedBy(context);
    }

    @Override
    public String getFailureMessage() {
        return leftPolicy.getFailureMessage() + " OR " + rightPolicy.getFailureMessage();
    }
}