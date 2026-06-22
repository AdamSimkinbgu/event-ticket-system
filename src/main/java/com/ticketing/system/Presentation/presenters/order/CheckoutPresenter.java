package com.ticketing.system.Presentation.presenters.order;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import com.ticketing.system.Core.Application.dto.ActiveOrderDTO;
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

    public Identity resolveIdentity() {
        if (identity.isMember()) {
            return new Identity(true, identity.memberToken(), null, identity.memberUserId());
        }
        return new Identity(false, null, identity.guestSessionId(), 0);
    }

    public record Identity(boolean member, String memberToken, String guestSessionId, int userId) { }

    // ---- load -----------------------------------------------------------

    public LoadOutcome loadOrder(int userId, String memberToken, String guestSessionId) {
        try {
            if (memberToken != null && userId > 0) {
                return classify(reservationService.restoreActiveOrder(userId));
            }
            if (guestSessionId != null) {
                return classify(reservationService.restoreActiveOrderForGuest(guestSessionId));
            }
            return new LoadOutcome.NotAuthenticated();
        } catch (RuntimeException e) {
            return new LoadOutcome.Failure(e.getMessage());
        }
    }

    private LoadOutcome classify(ActiveOrderDTO order) {
        if (order == null || order.lines().isEmpty()) {
            return new LoadOutcome.Empty();
        }
        return new LoadOutcome.Loaded(order, price(order));
    }

    private Pricing price(ActiveOrderDTO order) {
        long subtotal = order.lines().stream()
                .mapToLong(l -> Money.toCents(l.pricePerTicket()))
                .sum();
        return new Pricing(subtotal, subtotal);
    }

    public record Pricing(long subtotalCents, long totalCents) { }

    // ---- pay ------------------------------------------------------------

        public PayOutcome payAsMember(String memberToken, String idempotencyKey, String rawCardNumber) {
        return runPay(() -> checkoutService.checkoutMember(
            memberToken, idempotencyKey, CURRENCY, paymentToken(rawCardNumber)));
    }

    public PayOutcome payAsGuest(String sessionId, String guestEmail, int guestAge,
                                 String idempotencyKey, String rawCardNumber) {
        return runPay(() -> checkoutService.checkoutGuest(
            sessionId, guestEmail, idempotencyKey, CURRENCY, paymentToken(rawCardNumber), guestAge));
    }

    private interface Charge { CheckoutResultDTO run(); }

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

    private static String paymentToken(String cardNumber) {
        return "tok_" + cardNumber.replaceAll("\\s+", "");
    }

    // ---- session --------------------------------------------------------

    public void setOrderSession(CheckoutResultDTO result) {
        VaadinSession s = VaadinSession.getCurrent();
        if (s != null) {
            s.setAttribute("checkout.result", result);
        }
    }

    // ---- order-expiry push ---------------------------------------------

    public interface ExpiryListener {
        boolean matches(int userId, String sessionId);
        void onExpired();
    }

    public void registerExpiryListener(ExpiryListener listener) {
        expiryListeners.add(listener);
    }

    public void unregisterExpiryListener(ExpiryListener listener) {
        expiryListeners.remove(listener);
    }

    @EventListener
    public void onOrderExpired(OrderExpiredEvent event) {
        for (ExpiryListener listener : expiryListeners) {
            if (listener.matches(event.userId(), event.sessionId())) {
                listener.onExpired();
            }
        }
    }

    // ---- outcomes -------------------------------------------------------

    public sealed interface LoadOutcome {
        record Loaded(ActiveOrderDTO order, Pricing pricing) implements LoadOutcome { }
        record Empty()                implements LoadOutcome { }
        record NotAuthenticated()     implements LoadOutcome { }
        record Failure(String reason) implements LoadOutcome { }
    }

    public sealed interface PayOutcome {
        record Success(CheckoutResultDTO result)     implements PayOutcome { }
        record PolicyRejected(String reason)         implements PayOutcome { }
        record PaymentDeclined(String reason)        implements PayOutcome { }
        record SoldOut(String reason)                implements PayOutcome { }
        record OrderExpired(String reason)           implements PayOutcome { }
        record DuplicateSubmission()                 implements PayOutcome { }
        record Failure(String reason)                implements PayOutcome { }
    }
}