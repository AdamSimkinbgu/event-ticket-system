package com.ticketing.system.Core.Domain.policies.purchase;

import com.ticketing.system.Core.Domain.shared.InvariantChecked;

/**
 * Immutable snapshot of the inputs a {@link PurchasePolicy} evaluates: who is
 * buying, for which event/company, how many tickets, and at which
 * {@link PurchaseStage}. Decoupling the rules from the order aggregate keeps the
 * policy tree free of persistence concerns.
 *
 * <p>{@code buyerAge} is nullable — it is unknown for a guest until checkout.
 */
public class PurchaseContext implements InvariantChecked {

    private final int buyerId;
    private final Integer buyerAge;
    private final int eventId;
    private final int companyId;
    private final int quantity;

    private final PurchaseStage stage;

    /**
     * Final-checkout context (every rule enforced).
     *
     * @param buyerId   the buyer's user id
     * @param buyerAge  the buyer's age in years, or {@code null} if unknown
     * @param eventId   the event being purchased from
     * @param companyId the company that owns the event
     * @param quantity  the number of tickets (must be positive)
     * @throws IllegalArgumentException if {@code quantity} is not positive
     */
    public PurchaseContext(int buyerId, Integer buyerAge, int eventId, int companyId, int quantity) {
        this(buyerId, buyerAge, eventId, companyId, quantity, PurchaseStage.CHECKOUT);
    }

    /**
     * @param buyerId   the buyer's user id
     * @param buyerAge  the buyer's age in years, or {@code null} if unknown
     * @param eventId   the event being purchased from
     * @param companyId the company that owns the event
     * @param quantity  the number of tickets (must be positive)
     * @param stage     the evaluation stage; defaults to
     *                  {@link PurchaseStage#CHECKOUT} when {@code null}
     * @throws IllegalArgumentException if {@code quantity} is not positive
     */
    public PurchaseContext(int buyerId, Integer buyerAge, int eventId, int companyId, int quantity,
            PurchaseStage stage) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        this.buyerId = buyerId;
        this.buyerAge = buyerAge;
        this.eventId = eventId;
        this.companyId = companyId;
        this.quantity = quantity;
        this.stage = stage == null ? PurchaseStage.CHECKOUT : stage;
        checkInvariants();
    }

    /** @return the stage at which the policy is being evaluated */
    public PurchaseStage getStage() {
        return stage;
    }

    /**
     * @throws IllegalStateException if {@code quantity} is not positive
     */
    @Override
    public void checkInvariants() {
        if (quantity <= 0) {
            throw new IllegalStateException("PurchaseContext invariant violated: quantity must be positive (was " + quantity + ")");
        }
    }

    /** @return the buyer's user id */
    public int getBuyerId() {
        return buyerId;
    }

    /** @return the buyer's age in years, or {@code null} if unknown */
    public Integer getBuyerAge() {
        return buyerAge;
    }

    /** @return the event being purchased from */
    public int getEventId() {
        return eventId;
    }

    /** @return the company that owns the event */
    public int getCompanyId() {
        return companyId;
    }

    /** @return the number of tickets being purchased */
    public int getQuantity() {
        return quantity;
    }
}
