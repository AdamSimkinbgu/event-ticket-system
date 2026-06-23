package com.ticketing.system.Presentation.presenters;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Domain.exceptions.AuthenticationFailedException;
import com.ticketing.system.Core.Domain.exceptions.EventNotOnSaleException;
import com.ticketing.system.Core.Domain.exceptions.GuestSessionRequiredException;
import com.ticketing.system.Core.Domain.exceptions.IdempotencyConflictException;
import com.ticketing.system.Core.Domain.exceptions.InvalidEmailFormatException;
import com.ticketing.system.Core.Domain.exceptions.InvalidStateTransitionException;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Core.Domain.exceptions.PaymentGatewayException;
import com.ticketing.system.Core.Domain.exceptions.PolicyViolationException;
import com.ticketing.system.Core.Domain.exceptions.DuplicateEmailException;
import com.ticketing.system.Core.Domain.exceptions.DuplicateUsernameException;
import com.ticketing.system.Core.Domain.exceptions.WeakPasswordException;
import com.ticketing.system.Presentation.components.ErrorPayload;

/**
 * Translates domain exceptions into {@link ErrorPayload} instances and
 * wraps service calls in a typed {@link Outcome}.
 *
 * <p>Presenters that don't need custom failure variants can delegate
 * entirely:
 * <pre>
 *   return translator.wrap(() -> service.doSomething(token));
 * </pre>
 *
 * <p>Presenters with rich sealed hierarchies (LoginPresenter,
 * CheckoutPresenter) catch their domain-specific cases first and fall
 * through to {@link #toPayload(RuntimeException)} for the generic ones,
 * so the {@link com.ticketing.system.Presentation.components.ErrorCode}
 * lands consistently in telemetry.
 */
@Component
public class ExceptionTranslator {

    /** Wraps a value-returning service call. */
    public <T> Outcome<T> wrap(Supplier<T> call) {
        try {
            return new Outcome.Success<>(call.get());
        } catch (RuntimeException e) {
            return new Outcome.Failure<>(toPayload(e));
        }
    }

    /** Wraps a void service call. */
    public Outcome<Void> wrapVoid(Runnable call) {
        return wrap(() -> { call.run(); return null; });
    }

    /**
     * Maps a domain exception to the canonical {@link ErrorPayload}.
     * Static so presenters with rich sealed hierarchies can call it
     * without injecting the full translator.
     */
    public static ErrorPayload toPayload(RuntimeException e) {
        return switch (e) {
            case AuthenticationFailedException ex   -> ErrorPayload.authFailed();
            case GuestSessionRequiredException ex   -> ErrorPayload.guestSessionExpired();
            case InvalidTokenException ex           -> ErrorPayload.guestSessionExpired();
            case DuplicateUsernameException ex      -> ErrorPayload.usernameTaken();
            case DuplicateEmailException ex         -> ErrorPayload.emailTaken();
            case WeakPasswordException ex           -> ErrorPayload.weakPassword(ex.getMessage());
            case InvalidEmailFormatException ex     -> ErrorPayload.invalidEmail();
            case EventNotOnSaleException ex         -> ErrorPayload.eventNotOnSale();
            case PolicyViolationException ex        -> ErrorPayload.policyViolation(ex.getMessage());
            case InvalidStateTransitionException ex -> ErrorPayload.invalidState(ex.getMessage());
            case PaymentGatewayException ex         -> ErrorPayload.paymentFailed(ex.getMessage());
            case IdempotencyConflictException ex    -> ErrorPayload.idempotencyConflict();
            default                                 -> ErrorPayload.unknown(e.getMessage());
        };
    }
}
