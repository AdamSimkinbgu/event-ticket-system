package com.ticketing.system.Core.Application.events;

/**
 * Published by SessionAndOrderSweeper when it expires an active order.
 * Listened to by CheckoutView (per-session) to refresh / redirect.
 *
 * @param userId    0 for guest carts
 * @param sessionId null for member carts
 */
public record OrderExpiredEvent(int userId, String sessionId) { }
