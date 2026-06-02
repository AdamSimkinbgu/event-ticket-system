package com.ticketing.system.Core.Domain.events;

public class PurchasePolicy {
    //TODO: needs to be implemented according to the requirements, for now it just has a simple limit on the number of tickets that can be purchased in a single transaction.
    private int limit;

    public PurchasePolicy(int limit) {
        this.limit = limit;
    }

    public boolean validate(int quantity) {
        boolean isValid = true;

        if (quantity <= 0) {
            isValid = false;
        }
        
        if (quantity > limit) {
            isValid = false;
        }

        return isValid;
    }

    
}
