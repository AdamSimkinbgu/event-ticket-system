package com.ticketing.system.Core.Domain.policies.purchase;

public class NoPurchasePolicy implements PurchasePolicy {

    @Override
    public boolean isSatisfiedBy(PurchaseContext context) {
        return true;
    }

    @Override
    public String getFailureMessage() {
        return "";
    }
}