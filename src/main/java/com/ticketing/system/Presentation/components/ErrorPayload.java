package com.ticketing.system.Presentation.components;

/**
 * Structured failure payload carried by presenter Failure outcomes and
 * rendered by {@link Toasts#failure(ErrorPayload)}.
 *
 * <p>{@code code} is a stable enum value safe for telemetry / logging.
 * {@code retryable} drives toast colour (amber vs red) and the optional
 * Retry action. {@code helpHref} — when non-null — adds a "Learn more"
 * anchor to the toast.
 *
 * <p>Use the static factory methods rather than the canonical constructor
 * so call sites read as intent, not as field-list noise.
 */
public record ErrorPayload(ErrorCode code, String message, boolean retryable, String helpHref) {

    // --- factories ---

    public static ErrorPayload authFailed() {
        return new ErrorPayload(ErrorCode.AUTH_FAILED, "Invalid username or password.", false, null);
    }

    public static ErrorPayload guestSessionExpired() {
        return new ErrorPayload(ErrorCode.GUEST_SESSION_EXPIRED,
                "Your session has expired. Please refresh the page.", false, null);
    }

    public static ErrorPayload usernameTaken() {
        return new ErrorPayload(ErrorCode.USERNAME_TAKEN, "That username is already taken.", false, null);
    }

    public static ErrorPayload emailTaken() {
        return new ErrorPayload(ErrorCode.EMAIL_TAKEN, "An account with that email already exists.", false, null);
    }

    public static ErrorPayload weakPassword(String detail) {
        String msg = (detail != null && !detail.isBlank()) ? detail : "Password does not meet the strength requirements.";
        return new ErrorPayload(ErrorCode.WEAK_PASSWORD, msg, false, null);
    }

    public static ErrorPayload invalidEmail() {
        return new ErrorPayload(ErrorCode.INVALID_EMAIL, "Please enter a valid email address.", false, null);
    }

    public static ErrorPayload eventNotOnSale() {
        return new ErrorPayload(ErrorCode.EVENT_NOT_ON_SALE, "Tickets for this event are not currently on sale.", false, null);
    }

    public static ErrorPayload policyViolation(String detail) {
        String msg = (detail != null && !detail.isBlank()) ? detail : "Your purchase does not meet the event's purchase policy.";
        return new ErrorPayload(ErrorCode.POLICY_VIOLATION, msg, false, null);
    }

    public static ErrorPayload invalidState(String detail) {
        String msg = (detail != null && !detail.isBlank()) ? detail : "This action cannot be performed in the current state.";
        return new ErrorPayload(ErrorCode.INVALID_STATE, msg, false, null);
    }

    public static ErrorPayload paymentFailed(String detail) {
        String msg = (detail != null && !detail.isBlank()) ? detail : "Payment could not be processed. Please try again.";
        return new ErrorPayload(ErrorCode.PAYMENT_FAILED, msg, true, null);
    }

    public static ErrorPayload idempotencyConflict() {
        return new ErrorPayload(ErrorCode.IDEMPOTENCY_CONFLICT,
                "This order was already submitted. Check your order history.", false, null);
    }

    public static ErrorPayload unknown(String detail) {
        String msg = (detail != null && !detail.isBlank()) ? detail : "Something went wrong. Please try again.";
        return new ErrorPayload(ErrorCode.UNKNOWN, msg, true, null);
    }
}
