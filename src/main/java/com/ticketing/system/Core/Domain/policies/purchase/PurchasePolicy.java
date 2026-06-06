package com.ticketing.system.Core.Domain.policies.purchase;

public interface PurchasePolicy {

    boolean isSatisfiedBy(PurchaseContext context);

    String getFailureMessage();
}