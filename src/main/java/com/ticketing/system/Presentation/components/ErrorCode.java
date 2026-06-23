package com.ticketing.system.Presentation.components;

public enum ErrorCode {
    AUTH_FAILED,
    GUEST_SESSION_EXPIRED,
    USERNAME_TAKEN,
    EMAIL_TAKEN,
    WEAK_PASSWORD,
    INVALID_EMAIL,
    EVENT_NOT_ON_SALE,
    POLICY_VIOLATION,
    INVALID_STATE,
    PAYMENT_FAILED,
    IDEMPOTENCY_CONFLICT,
    UNKNOWN
}
