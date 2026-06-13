package com.ticketing.system.Core.Domain.policies.purchase;

public class PurchaseContext {

    private final int buyerId;
    private final Integer buyerAge;
    private final int eventId;
    private final int companyId;
    private final int quantity;

    public PurchaseContext(int buyerId, Integer buyerAge, int eventId, int companyId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        this.buyerId = buyerId;
        this.buyerAge = buyerAge;
        this.eventId = eventId;
        this.companyId = companyId;
        this.quantity = quantity;
    }

    public int getBuyerId() {
        return buyerId;
    }

    public Integer getBuyerAge() {
        return buyerAge;
    }

    public int getEventId() {
        return eventId;
    }

    public int getCompanyId() {
        return companyId;
    }

    public int getQuantity() {
        return quantity;
    }
}