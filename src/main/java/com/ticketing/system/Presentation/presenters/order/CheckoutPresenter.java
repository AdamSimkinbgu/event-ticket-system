package com.ticketing.system.Presentation.presenters.order;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import com.ticketing.system.Core.Application.dto.ActiveOrderDTO;
import com.ticketing.system.Core.Application.dto.CardDetailsDTO;
import com.ticketing.system.Core.Application.dto.CheckoutResultDTO;
import com.ticketing.system.Core.Application.events.OrderExpiredEvent;
import com.ticketing.system.Core.Application.services.CheckoutService;
import com.ticketing.system.Core.Application.services.ReservationService;
import com.ticketing.system.Core.Domain.exceptions.IdempotencyConflictException;
import com.ticketing.system.Core.Domain.exceptions.InsufficientInventoryException;
import com.ticketing.system.Core.Domain.exceptions.InvalidStateTransitionException;
import com.ticketing.system.Core.Domain.exceptions.PaymentGatewayException;
import com.ticketing.system.Core.Domain.exceptions.PolicyViolationException;
import com.ticketing.system.Presentation.components.Money;
import com.ticketing.system.Presentation.session.SessionIdentity;
import com.vaadin.flow.server.VaadinSession;

/**
 * MVP presenter for the checkout view. Resolves the buyer identity, loads and
 * prices the active order, drives {@link CheckoutService} for member/guest
 * payment, and translates domain exceptions into sealed {@link PayOutcome}
 * results the view renders. Also relays order-expiry push events to registered
 * view listeners.
 *
 * <p>It deliberately maps each domain failure to a distinct outcome so the
 * gateway/domain — not the presenter — remains the single source of a decline.
 */
@Component
@Slf4j
public class CheckoutPresenter {

    private static final String CURRENCY = "USD";

    private final ReservationService reservationService;
    private final CheckoutService    checkoutService;
    private final SessionIdentity    identity;

    private final Set<ExpiryListener> expiryListeners = ConcurrentHashMap.newKeySet();

    @Autowired
    public CheckoutPresenter(ReservationService reservationService,
                             CheckoutService    checkoutService,
                             SessionIdentity    identity) {
        this.reservationService = reservationService;
        this.checkoutService    = checkoutService;
        this.identity           = identity;
    }

    // ---- identity -------------------------------------------------------

    /**
     * @return the current buyer's identity (member token or guest session id)
     */
    public Identity resolveIdentity() {
        if (identity.isMember()) {
            return new Identity(true, identity.memberToken(), null, identity.memberUserId());
        }
        return new Identity(false, null, identity.guestSessionId(), 0);
    }

    /** The resolved buyer identity: member (token + userId) or guest (sessionId). */
    public record Identity(boolean member, String memberToken, String guestSessionId, int userId) { }

    // ---- load -----------------------------------------------------------

    /**
     * Loads and prices the buyer's active order for the checkout page. Uses the
     * credential lookup that tries the member key and falls back to the guest key,
     * so a cart stored under a userId is still found.
     *
     * @param userId         the member user id (unused; kept for signature symmetry)
     * @param memberToken    the member token (unused; resolved via identity)
     * @param guestSessionId the guest session id (unused; resolved via identity)
     * @return {@link LoadOutcome.Loaded} with order + pricing, {@link LoadOutcome.Empty},
     *         or {@link LoadOutcome.Failure} on error
     */
    public LoadOutcome loadOrder(int userId, String memberToken, String guestSessionId) {
        try {
            // Use the cart's exact lookup: viewMyActiveOrder tries the member key (token -> userId) and
            // FALLS BACK to the guest key, so a cart stored under userId (with sessionId null) is still
            // found. The old two-branch version committed to one key with no fallback -> empty checkout.
            return classify(reservationService.viewMyActiveOrder(identity.credential()));
        } catch (RuntimeException e) {
            return new LoadOutcome.Failure(e.getMessage());
        }
    }

    /**
     * @param order the active order DTO (may be null/empty)
     * @return {@link LoadOutcome.Empty} if there is nothing to check out, else
     *         {@link LoadOutcome.Loaded} with the order and its pricing
     */
    private LoadOutcome classify(ActiveOrderDTO order) {
        if (order == null || order.lines().isEmpty()) {
            return new LoadOutcome.Empty();
        }
        return new LoadOutcome.Loaded(order, price(order));
    }

    /**
     * @param order the active order DTO
     * @return the order pricing (subtotal and total in cents; currently equal)
     */
    private Pricing price(ActiveOrderDTO order) {
        long subtotal = order.lines().stream()
                .mapToLong(l -> Money.toCents(l.pricePerTicket()))
                .sum();
        return new Pricing(subtotal, subtotal);
    }

    /** Order pricing in integer cents. */
    public record Pricing(long subtotalCents, long totalCents) { }

    // ---- pay ------------------------------------------------------------

    /**
     * Pays for the order as a logged-in member.
     *
     * @param memberToken    the member's token
     * @param idempotencyKey the dedupe key for this submission
     * @param cardNumber     the card number
     * @param cvc            the card CVC
     * @param expiry         the card expiry as "MM / YY"
     * @param holder         the cardholder name
     * @return the {@link PayOutcome} (success or a typed failure)
     */
    public PayOutcome payAsMember(String memberToken, String idempotencyKey,
                                  String cardNumber, String cvc, String expiry, String holder) {
        CardDetailsDTO card = buildCard(cardNumber, cvc, expiry, holder);
        return runPay(() -> checkoutService.checkoutMember(memberToken, idempotencyKey, CURRENCY, card));
    }

    /**
     * Pays for the order as a guest.
     *
     * @param sessionId      the guest session id
     * @param guestEmail     the guest's contact email
     * @param guestAge       the guest's age (for purchase-policy checks)
     * @param idempotencyKey the dedupe key for this submission
     * @param cardNumber     the card number
     * @param cvc            the card CVC
     * @param expiry         the card expiry as "MM / YY"
     * @param holder         the cardholder name
     * @return the {@link PayOutcome} (success or a typed failure)
     */
    public PayOutcome payAsGuest(String sessionId, String guestEmail, int guestAge, String idempotencyKey,
                                 String cardNumber, String cvc, String expiry, String holder) {
        CardDetailsDTO card = buildCard(cardNumber, cvc, expiry, holder);
        return runPay(() -> checkoutService.checkoutGuest(
            sessionId, guestEmail, idempotencyKey, CURRENCY, card, guestAge));
    }

    private interface Charge { CheckoutResultDTO run(); }

    /**
     * Runs a charge and maps each domain exception to its typed {@link PayOutcome}.
     *
     * @param charge the checkout call to run
     * @return {@link PayOutcome.Success} or the outcome matching the failure cause
     */
    private PayOutcome runPay(Charge charge) {
        try {
            return new PayOutcome.Success(charge.run());
        } catch (RuntimeException e) {
            Throwable cause = (e.getCause() != null) ? e.getCause() : e;
            if (cause instanceof PolicyViolationException)       return new PayOutcome.PolicyRejected(cause.getMessage());
            if (cause instanceof PaymentGatewayException)        return new PayOutcome.PaymentDeclined(cause.getMessage());
            if (cause instanceof InsufficientInventoryException) return new PayOutcome.SoldOut(cause.getMessage());
            if (cause instanceof InvalidStateTransitionException) return new PayOutcome.OrderExpired("Order expired during checkout");
            if (cause instanceof IdempotencyConflictException)   return new PayOutcome.DuplicateSubmission();
            log.error("Checkout failed unexpectedly", e);
            return new PayOutcome.Failure(cause.getMessage());
        }
    }

    /**
     * Builds the gateway card payload from the raw checkout-form inputs. Parses the
     * "MM / YY" expiry into month + 4-digit year; malformed parts fall back to 0 so
     * the gateway, not the presenter, is the single source of a decline.
     *
     * @param cardNumber the raw card number (spaces stripped)
     * @param cvc        the card CVC
     * @param expiry     the expiry string, expected as "MM / YY"
     * @param holder     the cardholder name
     * @return the card details DTO for the gateway
     */
    private static CardDetailsDTO buildCard(String cardNumber, String cvc, String expiry, String holder) {
        int month = 0;
        int year = 0;
        if (expiry != null && expiry.contains("/")) {
            String[] parts = expiry.split("/");
            month = parseIntOrZero(parts[0].trim());
            int yy = parts.length > 1 ? parseIntOrZero(parts[1].trim()) : 0;
            year = (yy > 0 && yy < 100) ? 2000 + yy : yy;
        }
        String digits = cardNumber == null ? "" : cardNumber.replaceAll("\\s+", "");
        return new CardDetailsDTO(
            digits,
            cvc == null ? "" : cvc.trim(),
            month,
            year,
            holder == null ? "" : holder.trim());
    }

    /**
     * @param value the string to parse
     * @return the parsed int, or 0 if it is not a valid integer
     */
    private static int parseIntOrZero(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---- session --------------------------------------------------------

    /**
     * Stores the checkout result on the Vaadin session so the confirmation view can
     * read it after navigation.
     *
     * @param result the completed checkout result
     */
    public void setOrderSession(CheckoutResultDTO result) {
        VaadinSession s = VaadinSession.getCurrent();
        if (s != null) {
            s.setAttribute("checkout.result", result);
        }
    }

    // ---- order-expiry push ---------------------------------------------

    /** A view callback notified when the buyer's order expires while on the page. */
    public interface ExpiryListener {
        /**
         * @param userId    the expired order's member id
         * @param sessionId the expired order's guest session id
         * @return whether this listener represents that buyer
         */
        boolean matches(int userId, String sessionId);
        /** Invoked when the matching order has expired. */
        void onExpired();
    }

    /**
     * Registers a view listener for order-expiry push notifications.
     *
     * @param listener the listener to add
     */
    public void registerExpiryListener(ExpiryListener listener) {
        expiryListeners.add(listener);
    }

    /**
     * Removes a previously registered expiry listener.
     *
     * @param listener the listener to remove
     */
    public void unregisterExpiryListener(ExpiryListener listener) {
        expiryListeners.remove(listener);
    }

    /**
     * Relays an {@link OrderExpiredEvent} to every matching registered listener.
     *
     * @param event the order-expiry event
     */
    @EventListener
    public void onOrderExpired(OrderExpiredEvent event) {
        for (ExpiryListener listener : expiryListeners) {
            if (listener.matches(event.userId(), event.sessionId())) {
                listener.onExpired();
            }
        }
    }

    // ---- outcomes -------------------------------------------------------

    /** Result of {@link #loadOrder(int, String, String)}. */
    public sealed interface LoadOutcome {
        /** The order loaded: render it with the given pricing. */
        record Loaded(ActiveOrderDTO order, Pricing pricing) implements LoadOutcome { }
        /** Nothing to check out. */
        record Empty()                implements LoadOutcome { }
        /** No active credential. */
        record NotAuthenticated()     implements LoadOutcome { }
        /** Loading failed: show the reason. */
        record Failure(String reason) implements LoadOutcome { }
    }

    /** Result of a pay attempt — one variant per distinguishable failure mode. */
    public sealed interface PayOutcome {
        /** Payment succeeded: carries the checkout result. */
        record Success(CheckoutResultDTO result)     implements PayOutcome { }
        /** A purchase policy rejected the order. */
        record PolicyRejected(String reason)         implements PayOutcome { }
        /** The payment gateway declined the charge. */
        record PaymentDeclined(String reason)        implements PayOutcome { }
        /** Inventory sold out before the sale committed. */
        record SoldOut(String reason)                implements PayOutcome { }
        /** The reservation expired during checkout. */
        record OrderExpired(String reason)           implements PayOutcome { }
        /** The same idempotency key was resubmitted. */
        record DuplicateSubmission()                 implements PayOutcome { }
        /** An unexpected failure: show the reason. */
        record Failure(String reason)                implements PayOutcome { }
    }
}