package com.ticketing.system.Core.Application.events;

public class OrderExpiredEvent {

    private final int    userId;    // 0 for guest carts
    private final String sessionId; // null for member-only carts

    public OrderExpiredEvent(int userId, String sessionId) {
        this.userId    = userId;
        this.sessionId = sessionId;
    }

    public int    getUserId()    { return userId; }
    public String getSessionId() { return sessionId; }
}
