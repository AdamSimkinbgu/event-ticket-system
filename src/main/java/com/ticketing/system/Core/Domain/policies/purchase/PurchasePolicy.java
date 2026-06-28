package com.ticketing.system.Core.Domain.policies.purchase;

/**
 * A composable purchase rule (Strategy + Composite pattern). An implementation
 * decides whether a given {@link PurchaseContext} may proceed and, when it may
 * not, supplies a buyer-facing explanation.
 *
 * <p>Leaf rules ({@link AgePurchasePolicy}, {@link MinTicketsPurchasePolicy},
 * {@link MaxTicketsPurchasePolicy}, {@link NoPurchasePolicy}) are combined with
 * {@link AndPurchasePolicy} / {@link OrPurchasePolicy} into a tree that is
 * <em>evaluated</em>, never queried by sub-policy. The whole tree is persisted
 * as JSON via {@link PurchasePolicyJsonConverter}. UC-21 (policy authoring),
 * UC-9 / UC-10 (enforcement at reserve / checkout).
 */
public interface PurchasePolicy {

    /**
     * @param context the buyer, cart and {@link PurchaseStage} being evaluated
     * @return {@code true} if this policy permits the purchase
     */
    boolean isSatisfiedBy(PurchaseContext context);

    /**
     * @return a buyer-facing explanation of why {@link #isSatisfiedBy} returned
     *         {@code false}; an empty string when the policy imposes no
     *         restriction
     */
    String getFailureMessage();
}
