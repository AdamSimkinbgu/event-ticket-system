package com.ticketing.system.Presentation.presenters.order;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.ActiveOrderDTO;
import com.ticketing.system.Core.Application.dto.CheckoutResultDTO;
import com.ticketing.system.Core.Application.events.OrderExpiredEvent;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.services.CheckoutService;
import com.ticketing.system.Core.Application.services.ReservationService;
import com.ticketing.system.Core.Domain.exceptions.IdempotencyConflictException;
import com.ticketing.system.Core.Domain.exceptions.InsufficientInventoryException;
import com.ticketing.system.Core.Domain.exceptions.PaymentGatewayException;
import com.ticketing.system.Presentation.components.Money;

import lombok.extern.slf4j.Slf4j;

/**
 * MVP presenter for {@code CheckoutView}. No Vaadin imports.
 *
 * <p>It owns three things the view must not: (1) identity resolution via
 * {@link ISessionManager}; (2) the {@link OrderExpiredEvent} subscription,
 * re-dispatched to views through a Vaadin-free {@link ExpiryListener} so the
 * view never imports a Core type; (3) order pricing.
 *
 * <p>NOTE: {@code CheckoutService} wraps every failure in
 * {@code RuntimeException("Checkout failed…", cause)}, so {@link #runPay} unwraps
 * {@code getCause()} to recover the typed domain exception.
 */
@Component
@Slf4j
public class CheckoutPresenter {

    private static final String CURRENCY = "USD";

    private final ReservationService reservationService;
    private final CheckoutService    checkoutService;
    private final ISessionManager    sessionManager;

   
    private final Set<ExpiryListener> expiryListeners = ConcurrentHashMap.newKeySet();

    @Autowired
    public CheckoutPresenter(ReservationService reservationService,
                             CheckoutService    checkoutService,
                             ISessionManager    sessionManager) {
        this.reservationService = reservationService;
        this.checkoutService    = checkoutService;
        this.sessionManager     = sessionManager;
    }

   
     
    public Identity resolveIdentity(String memberToken) {
        try {
            if (memberToken != null && sessionManager.validateToken(memberToken)) {
                return new Identity(true, sessionManager.extractUserId(memberToken));
            }
        } catch (RuntimeException e) {
            // not a valid member token — fall through to guest
        }
        return new Identity(false, null);
    }

    public record Identity(boolean member, Integer userId) { }

    public LoadOutcome loadOrder(String memberToken, String guestSessionId) {
        try {
            if (memberToken != null && sessionManager.validateToken(memberToken)) {
                int userId = sessionManager.extractUserId(memberToken);
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

    public static long toCents(double amount) {
        return Math.round(amount * 100);
    }


    public PayOutcome payAsMember(String memberToken, String rawCardNumber) {
        return runPay(() -> checkoutService.checkoutMember(
            memberToken, newKey(), CURRENCY, paymentToken(rawCardNumber)));
    }

    public PayOutcome payAsGuest(String sessionId, String guestEmail,
                                 int guestAge, String rawCardNumber) {
        return runPay(() -> checkoutService.checkoutGuest(
            sessionId, guestEmail, newKey(), CURRENCY, paymentToken(rawCardNumber), guestAge));
    }

    private interface Charge { CheckoutResultDTO run(); }

    private PayOutcome runPay(Charge charge) {
        try {
            return new PayOutcome.Success(charge.run());
        } catch (RuntimeException e) {
            // CheckoutService wraps everything in RuntimeException("Checkout failed…", cause).
            Throwable cause = (e.getCause() != null) ? e.getCause() : e;
            if (cause instanceof PaymentGatewayException)        return new PayOutcome.PaymentDeclined(cause.getMessage());
            if (cause instanceof InsufficientInventoryException) return new PayOutcome.SoldOut(cause.getMessage());
            if (cause instanceof IdempotencyConflictException)   return new PayOutcome.DuplicateSubmission();
            log.error("Checkout failed unexpectedly", e);
            return new PayOutcome.Failure(cause.getMessage());
        }
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private static String paymentToken(String cardNumber) {
        return "tok_" + cardNumber.replaceAll("\\s+", "");
    }


    public interface ExpiryListener {
        /** True if this expiry event concerns the order this listener cares about. */
        boolean matches(int userId, String sessionId);
        /** Invoked when a matching order expires (the View hops back onto the UI thread). */
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
        record Success(CheckoutResultDTO result) implements PayOutcome { }
        record PaymentDeclined(String reason)    implements PayOutcome { }
        record SoldOut(String reason)            implements PayOutcome { }
        record DuplicateSubmission()             implements PayOutcome { }
        record Failure(String reason)            implements PayOutcome { }
    }
}