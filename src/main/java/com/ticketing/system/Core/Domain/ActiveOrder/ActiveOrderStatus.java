package com.ticketing.system.Core.Domain.ActiveOrder;

/**
 * Lifecycle state of an {@code ActiveOrder} (the buyer's reservation/cart).
 */
public enum ActiveOrderStatus {
    /** The active order has not been checked out yet. */
    PRE_CHECKOUT,
    /** Checkout for the active order is in progress (Phase 1 of the 3-phase checkout). */
    CHECKOUT_IN_PROGRESS
}
