package com.ticketing.system.Core.Application.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.ticketing.system.Core.Application.dto.CardDetailsDTO;
import com.ticketing.system.Core.Application.dto.CheckoutResultDTO;
import com.ticketing.system.Core.Application.dto.IssuanceRequestDTO;
import com.ticketing.system.Core.Application.dto.IssuanceResultDTO;
import com.ticketing.system.Core.Application.dto.PaymentRequestDTO;
import com.ticketing.system.Core.Application.dto.PaymentResultDTO;
import com.ticketing.system.Core.Application.interfaces.INotificationService;
import com.ticketing.system.Core.Application.interfaces.IPaymentGateway;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.interfaces.ITicketIssuer;
import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;
import com.ticketing.system.Core.Domain.ActiveOrder.CartLineItem;
import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.InventorySelection;
import com.ticketing.system.Core.Domain.events.InventoryZone;
import com.ticketing.system.Core.Domain.events.Seat;
import com.ticketing.system.Core.Domain.events.SeatStatus;
import com.ticketing.system.Core.Domain.events.SeatedZone;
import com.ticketing.system.Core.Domain.exceptions.AuthenticationFailedException;
import com.ticketing.system.Core.Domain.exceptions.ConcurrentReservationException;
import com.ticketing.system.Core.Domain.exceptions.EventNotFoundException;
import com.ticketing.system.Core.Domain.exceptions.EventNotOnSaleException;
import com.ticketing.system.Core.Domain.exceptions.IdempotencyConflictException;
import com.ticketing.system.Core.Domain.exceptions.InsufficientInventoryException;
import com.ticketing.system.Core.Domain.exceptions.InvalidStateTransitionException;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Core.Domain.exceptions.MarketNotOpenException;
import com.ticketing.system.Core.Domain.exceptions.PaymentGatewayException;
import com.ticketing.system.Core.Domain.exceptions.SessionExpiredException;
import com.ticketing.system.Core.Domain.exceptions.TicketIssuanceFailedException;
import com.ticketing.system.Core.Domain.exceptions.UserNotFoundException;
import com.ticketing.system.Core.Domain.exceptions.EntityNotFoundException;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;
import com.ticketing.system.Core.Domain.orders.ReceiptLine;
import com.ticketing.system.Core.Domain.orders.TransactionRecord;
import com.ticketing.system.Core.Domain.policies.purchase.PurchaseContext;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.users.User;

/**
 * Orchestrates the checkout flow for both members and guests: validates input,
 * enforces idempotency, prices the cart, charges payment, issues tickets,
 * confirms the inventory sale, persists the receipt, and rolls back cleanly on
 * failure. UC-10 / UC-33 / UC-34.
 *
 * <p><b>Idempotency:</b> if the same buyer resubmits the same checkout request
 * (same idempotency key), only the first is processed and the cached result is
 * returned for the rest — preventing duplicate charges/orders on a double-click
 * or a network retry. The cache is keyed by buyer identity + idempotency key
 * (in-memory for V1; would be a distributed cache with a TTL in production).
 *
 * <p><b>3-phase structure</b> (minimizes lock hold time during slow I/O):
 * <ol>
 *   <li>Phase 1 (short order lock): validate, snapshot items, mark
 *       CHECKOUT_IN_PROGRESS, release the lock.</li>
 *   <li>Phase 2 (no domain locks): price items, charge payment, issue tickets.</li>
 *   <li>Phase 3 (short order + event locks): re-verify the reservation still
 *       belongs to this order, persist tickets/receipt, then commit the
 *       RESERVED→SOLD inventory sale as the point of no return.</li>
 * </ol>
 * The market-open gate (UC-32 / I.2.1) runs after the idempotency short-circuit
 * so a completed purchase is still replayable once the market has closed.
 */
@Service
@Slf4j
public class CheckoutService {

    private final IActiveOrderRepository activeOrderRepository;
    private final IEventRepository eventRepository;
    private final ITicketRepository ticketRepository;
    private final IOrderReceiptRepository orderReceiptRepository;
    private final ITicketIssuer ticketIssuer;
    private final IPaymentGateway paymentGateway;
    private final INotificationService notificationService;
    private final ISessionManager sessionManager;
    private final IUserRepository userRepository;
    private final IProductionCompanyRepository companyRepository;
    private final SystemAdminService systemAdminService;

    /**
     * In-memory cache of completed checkouts for idempotency. Keyed by idempotency
     * key, with the buyer identity stored in the entry so the same key reused by a
     * different buyer is detected as a conflict. In production this would be a
     * distributed cache (e.g. Redis) with a TTL.
     */
    private final ConcurrentMap<String, IdempotencyCacheEntry> completedCheckoutsByIdempotencyKey = new ConcurrentHashMap<>();

    public CheckoutService(
            IActiveOrderRepository activeOrderRepository,
            IEventRepository eventRepository,
            ITicketRepository ticketRepository,
            IOrderReceiptRepository orderReceiptRepository,
            ITicketIssuer ticketIssuer,
            IPaymentGateway paymentGateway,
            INotificationService notificationService,
            ISessionManager sessionManager,
            IUserRepository userRepository,
            IProductionCompanyRepository companyRepository,
            SystemAdminService systemAdminService) {
        this.activeOrderRepository = activeOrderRepository;
        this.eventRepository = eventRepository;
        this.ticketRepository = ticketRepository;
        this.orderReceiptRepository = orderReceiptRepository;
        this.ticketIssuer = ticketIssuer;
        this.paymentGateway = paymentGateway;
        this.notificationService = notificationService;
        this.sessionManager = sessionManager;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.systemAdminService = systemAdminService;
    }

    /**
     * UC-32 / I.2.1 — guards that no money moves while the trading market is
     * closed. A platform-wide gate, orthogonal to the per-event ON_SALE check in
     * Phase 3 ({@link #validateEventsStillOnSale}). Invoked as a guard clause so it
     * propagates cleanly instead of being wrapped as a generic checkout failure.
     *
     * @throws MarketNotOpenException if the market is not currently open
     */
    private void requireMarketOpen() {
        if (!systemAdminService.isMarketOpen()) {
            throw new MarketNotOpenException();
        }
    }

    /**
     * UC-10 / UC-33 / UC-34 — checks out the authenticated member's active order
     * (identified by user id) through the 3-phase flow described on the class. A
     * completed purchase is returned from the idempotency cache before the
     * market-open gate, so it replays even if the market has since closed.
     *
     * <p>On failure after Phase 1, reserved inventory is returned to stock and any
     * charge is refunded (unless the sale already committed); the exception is
     * rethrown wrapped as a checkout failure. A closed market is rethrown raw.
     *
     * @param token          the authenticated member's token
     * @param idempotencyKey the client-supplied key that dedupes retries
     * @param currency       the payment currency
     * @param card           the payment card details
     * @return the checkout result (total, receipt id, payment txn, issued tickets)
     * @throws MarketNotOpenException        if the market is closed (rethrown raw)
     * @throws IdempotencyConflictException  if the key was used by a different buyer
     * @throws RuntimeException              wrapping any mid-checkout failure (after
     *                                      rollback/refund)
     */
    public CheckoutResultDTO checkoutMember(String token, String idempotencyKey, String currency,
            CardDetailsDTO card) {
        int userId = -1;
        ActiveOrder order = null;
        PaymentResultDTO paymentResult = null;
        double totalPrice = 0.0;
        boolean inventorySaleConfirmed = false;
        boolean checkoutSucceeded = false;
        List<ReceiptLine> receiptLinesToNotifyAfterUnlock = null;

        // orderLockKey is for locking the ActiveOrder to prevent concurrent
        // modifications during checkout.
        String orderLockKey = null;
        // lockedEventIds tracks which events we have locked in Phase 3 so we can unlock
        // them in the finally block.
        List<Integer> lockedEventIds = List.of();

        try {
            userId = authenticateAndGetUserId(token);
            validatePaymentInput(idempotencyKey, currency, card, userId);

            String buyerKey = memberBuyerKey(userId);

            // Idempotency short-circuit BEFORE the market gate: a completed purchase must be returned even
            // if the market has since closed (C1). (Pre-flight validation failures above are still wrapped
            // by the catch as a checkout failure, matching the existing contract.)
            CheckoutResultDTO cached = getCachedCheckoutResult(idempotencyKey, buyerKey);
            if (cached != null) {
                return cached;
            }

            // UC-32 / I.2.1 — no money moves while the market is closed (checked after the idempotency
            // short-circuit). Rethrown raw by the MarketNotOpenException catch below.
            requireMarketOpen();

            // ---------------------------------------------------------------
            // Phase 1: short lock — validate, snapshot, freeze order
            // ---------------------------------------------------------------
            orderLockKey = memberOrderLockKey(userId);
            activeOrderRepository.lockForUpdate(orderLockKey);

            order = activeOrderRepository.getByUserId(userId);
            validateOrderForCheckout(order, userId);

            List<CartLineItem> snapshotItems = List.copyOf(order.getItems());
            String orderKey = order.getOrderKey();

            // Mark the order as checkout in progress and save it to the repository. This is
            // important for preventing other concurrent checkout
            // attempts on the same order and for providing visibility into the order's
            // state during the checkout process.
            // By marking the order as CHECKOUT_IN_PROGRESS, we can also implement logic in
            // other parts of the system
            // (e.g. inventory management) to treat this order differently while it is in
            // this state.
            order.markCheckoutInProgress();
            activeOrderRepository.save(order);

            activeOrderRepository.unlock(orderLockKey);

            // ---------------------------------------------------------------
            // Phase 2: no domain locks — slow external calls - cart is frozen here,
            // ---------------------------------------------------------------
            List<Integer> eventIds = extractSortedEventIds(snapshotItems);
            Integer buyerAge = getBuyerAgeByUserId(userId);
            validatePurchasePolicies(snapshotItems, userId, buyerAge);

            List<PricedCartLine> pricedItems = priceItemsOnce(snapshotItems);
            totalPrice = sumPrices(pricedItems);

            paymentResult = chargePayment(userId, null, totalPrice, idempotencyKey, currency, card);
            validatePaymentResult(paymentResult, totalPrice, currency);

            IssuanceResultDTO issuanceResult = issueTickets(userId, null, snapshotItems);
            validateIssuanceResult(issuanceResult, snapshotItems, userId);

            // ---------------------------------------------------------------
            // Phase 3: short locks — verify reservation ownership, re-check cart snapshot,
            // confirm, persist
            // ---------------------------------------------------------------
            activeOrderRepository.lockForUpdate(orderLockKey);
            order = activeOrderRepository.getByUserId(userId);

            validateOrderStillInCheckout(order);
            validateCheckoutSnapshotStillMatches(order, snapshotItems);

            lockedEventIds = eventIds;
            lockEvents(lockedEventIds);

            validateEventsStillOnSale(snapshotItems);
            // Fail-fast ownership check (read-only): the reservation must still be ours and
            // RESERVED.
            validateCanConfirmInventorySale(snapshotItems, orderKey);

            // Persist tickets + receipt BEFORE the irreversible RESERVED→SOLD confirmation.
            // If any of
            // this fails, the inventory is still RESERVED, so the normal
            // (!inventorySaleConfirmed)
            // rollback cleanly returns it to stock and refunds — nothing is stranded as
            // SOLD.
            int orderReceiptId = orderReceiptRepository.nextId();
            List<ReceiptLine> receiptLines = saveTicketsAndBuildReceiptLines(userId, orderReceiptId, pricedItems,
                    issuanceResult);
            saveMemberReceipt(userId, orderReceiptId, totalPrice, receiptLines, paymentResult, issuanceResult);

            // Point of no return: commit the sale last, once everything fallible has
            // succeeded.
            confirmInventorySale(snapshotItems, orderKey);
            inventorySaleConfirmed = true;

            // Point of no return passed: the sale is committed and the receipt is the durable record.
            // Consuming the cart (buy() + delete) is best-effort — a cleanup hiccup must never turn a
            // committed purchase into a checkout failure (C2). The cart isn't saved: buy() leaves it
            // CHECKOUT_IN_PROGRESS, so a save would strand an empty, unmodifiable cart that wedges the
            // buyer's next reservation.
            finalizeConsumedOrder(order);

            CheckoutResultDTO result = buildCheckoutResult(totalPrice, orderReceiptId, paymentResult, issuanceResult);
            cacheCheckoutResult(idempotencyKey, buyerKey, result);

            receiptLinesToNotifyAfterUnlock = receiptLines;
            checkoutSucceeded = true;

            return result;

        } catch (MarketNotOpenException marketClosed) {
            // Pre-Phase-1 gate failure: nothing was reserved or charged, so there is nothing to roll back.
            // Propagate it raw (callers/tests distinguish a closed market from a mid-checkout failure).
            throw marketClosed;
        } catch (Exception e) {
            handleCheckoutFailure(order, userId, orderLockKey, paymentResult, totalPrice, inventorySaleConfirmed,
                    e);
            // failure handling does not mutate inventory without locks
            // checkout failure handling will: reset CHECKOUT_IN_PROGRESS safely, refund
            // payment if needed, not release inventory without locks, not clear the cart
            // unsafely.
            throw new RuntimeException("Checkout failed, tickets returned to stock", e);
        } finally {
            unlockEvents(lockedEventIds);

            if (orderLockKey != null && activeOrderRepository != null) {
                try {
                    activeOrderRepository.unlock(orderLockKey);
                } catch (Exception ignored) {
                }
            }
            if (checkoutSucceeded && userId > 0 && receiptLinesToNotifyAfterUnlock != null) {
                try {
                    notifyPurchaseCompleted(userId, totalPrice, receiptLinesToNotifyAfterUnlock);
                } catch (RuntimeException notificationFailure) {
                    log.warn("Purchase completed but notification failed for userId={}", userId, notificationFailure);
                }
            }
        }
    }

    /**
     * UC-10 / UC-33 / UC-34 — the guest counterpart of {@link #checkoutMember}.
     * Identifies the buyer by guest session id + email and retrieves the active
     * order by session id, but otherwise follows the same 3-phase flow, idempotency
     * handling, and rollback/refund semantics. Presence and idempotency checks run
     * before the market gate; the session-liveness check runs just after it, so a
     * completed purchase replays without a live session.
     *
     * @param guestSessionId the guest session id
     * @param guestEmail     the guest's contact email (also receives the receipt)
     * @param idempotencyKey the client-supplied key that dedupes retries
     * @param currency       the payment currency
     * @param card           the payment card details
     * @param buyerAge       the guest's age, for purchase-policy evaluation
     * @return the checkout result (total, receipt id, payment txn, issued tickets)
     * @throws MarketNotOpenException        if the market is closed (rethrown raw)
     * @throws IdempotencyConflictException  if the key was used by a different buyer
     * @throws SessionExpiredException       if the guest session is no longer live
     * @throws RuntimeException              wrapping any mid-checkout failure (after
     *                                      rollback/refund)
     */
    public CheckoutResultDTO checkoutGuest(String guestSessionId, String guestEmail, String idempotencyKey,
            String currency, CardDetailsDTO card, int buyerAge) {
        ActiveOrder order = null;
        PaymentResultDTO paymentResult = null;
        double totalPrice = 0.0;
        boolean inventorySaleConfirmed = false;

        String orderLockKey = null;
        List<Integer> lockedEventIds = List.of();

        try {
            // Presence + input + idempotency short-circuit run BEFORE the market gate so a completed
            // purchase is returned even if the market has since closed (C1). The session-liveness check is
            // deferred to just after the gate: it doesn't affect the cache key, and the market gate must
            // win over a stale-session error to honour the market-closed contract.
            validateGuestIdentityPresent(guestSessionId, guestEmail);
            validatePaymentInput(idempotencyKey, currency, card, null);

            String buyerKey = guestBuyerKey(guestSessionId, guestEmail);

            CheckoutResultDTO cached = getCachedCheckoutResult(idempotencyKey, buyerKey);
            if (cached != null) {
                return cached;
            }

            requireMarketOpen();
            validateGuestSessionLive(guestSessionId);

            // ---------------------------------------------------------------
            // Phase 1: short lock — validate, snapshot, freeze order
            // ---------------------------------------------------------------
            orderLockKey = guestOrderLockKey(guestSessionId);
            activeOrderRepository.lockForUpdate(orderLockKey);

            order = activeOrderRepository.getBySessionId(guestSessionId)
                    .orElseThrow(() -> new EntityNotFoundException("Active guest order not found"));
            validateOrderForCheckout(order, null);

            List<CartLineItem> snapshotItems = List.copyOf(order.getItems());
            String orderKey = order.getOrderKey();

            order.markCheckoutInProgress();
            activeOrderRepository.save(order);

            activeOrderRepository.unlock(orderLockKey);

            // ---------------------------------------------------------------
            // Phase 2: no domain locks — slow external calls
            // ---------------------------------------------------------------
            List<Integer> eventIds = extractSortedEventIds(snapshotItems);
            validatePurchasePolicies(snapshotItems, null, buyerAge);

            List<PricedCartLine> pricedItems = priceItemsOnce(snapshotItems);
            totalPrice = sumPrices(pricedItems);

            paymentResult = chargePayment(null, guestEmail, totalPrice, idempotencyKey, currency, card);
            validatePaymentResult(paymentResult, totalPrice, currency);

            IssuanceResultDTO issuanceResult = issueTickets(null, guestEmail, snapshotItems);
            validateIssuanceResult(issuanceResult, snapshotItems, null);

            // ---------------------------------------------------------------
            // Phase 3: short locks — verify ownership, confirm, persist
            // ---------------------------------------------------------------
            activeOrderRepository.lockForUpdate(orderLockKey);
            order = activeOrderRepository.getBySessionId(guestSessionId)
                    .orElseThrow(() -> new EntityNotFoundException("Active guest order not found in Phase 3"));

            validateOrderStillInCheckout(order);
            validateCheckoutSnapshotStillMatches(order, snapshotItems);

            lockedEventIds = eventIds;
            lockEvents(lockedEventIds);

            validateEventsStillOnSale(snapshotItems);
            // Fail-fast ownership check (read-only): the reservation must still be ours and
            // RESERVED.
            validateCanConfirmInventorySale(snapshotItems, orderKey);

            // Persist tickets + receipt BEFORE the irreversible RESERVED→SOLD confirmation.
            // If any of
            // this fails, the inventory is still RESERVED, so the normal
            // (!inventorySaleConfirmed)
            // rollback cleanly returns it to stock and refunds — nothing is stranded as
            // SOLD.
            int orderReceiptId = orderReceiptRepository.nextId();
            List<ReceiptLine> receiptLines = saveTicketsAndBuildReceiptLines(null, orderReceiptId, pricedItems,
                    issuanceResult);
            saveGuestReceipt(guestEmail, guestSessionId, orderReceiptId, totalPrice, receiptLines, paymentResult,
                    issuanceResult);

            // Point of no return: commit the sale last, once everything fallible has
            // succeeded.
            confirmInventorySale(snapshotItems, orderKey);
            inventorySaleConfirmed = true;

            // Point of no return passed: the sale is committed and the receipt is the durable record.
            // Consuming the cart (buy() + delete) is best-effort — a cleanup hiccup must never turn a
            // committed purchase into a checkout failure (C2). The cart isn't saved: buy() leaves it
            // CHECKOUT_IN_PROGRESS, so a save would strand an empty, unmodifiable cart that wedges the
            // buyer's next reservation.
            finalizeConsumedOrder(order);

            CheckoutResultDTO result = buildCheckoutResult(totalPrice, orderReceiptId, paymentResult, issuanceResult);
            cacheCheckoutResult(idempotencyKey, buyerKey, result);

            return result;

        } catch (MarketNotOpenException marketClosed) {
            // Pre-Phase-1 gate failure: nothing reserved or charged, nothing to roll back. Propagate raw.
            throw marketClosed;
        } catch (Exception e) {
            handleGuestCheckoutFailure(order, guestSessionId, orderLockKey, paymentResult, totalPrice,
                    inventorySaleConfirmed, e);
            throw new RuntimeException("Checkout failed, tickets returned to stock", e);
        } finally {
            unlockEvents(lockedEventIds);
            if (orderLockKey != null) {
                try {
                    activeOrderRepository.unlock(orderLockKey);
                } catch (Exception ignored) {
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Helper methods for the checkout flow (auth, validation, pricing, payment,
    // issuance, inventory confirmation, receipt saving, notifications, caching,
    // error handling) — shared by member and guest checkout where applicable.
    // ---------------------------------------------------------------------------

    /**
     * Validates member checkout identity: the token must be present, valid (an
     * active session), and resolve to a positive user id.
     *
     * @param token the authentication token
     * @return the authenticated member's user id
     * @throws InvalidTokenException         if the token is missing/blank
     * @throws AuthenticationFailedException if the token is invalid
     * @throws UserNotFoundException         if the token resolves to a non-positive id
     */
    private int authenticateAndGetUserId(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Missing authentication token");
        }

        if (!sessionManager.validateToken(token)) {
            throw new AuthenticationFailedException();
        }

        int userId = sessionManager.extractUserId(token);
        if (userId <= 0) {
            throw new UserNotFoundException("Invalid user id in token");
        }

        return userId;
    }

    /**
     * Presence checks for guest identity (session id + email) — these run before
     * the market gate because they are needed to form the idempotency cache key.
     *
     * @param guestSessionId the guest session id
     * @param guestEmail     the guest's email
     * @throws InvalidTokenException if either value is missing/blank
     */
    private void validateGuestIdentityPresent(String guestSessionId, String guestEmail) {
        if (guestSessionId == null || guestSessionId.isBlank()) {
            throw new InvalidTokenException("guestSessionId is required");
        }

        if (guestEmail == null || guestEmail.isBlank()) {
            throw new InvalidTokenException("guestEmail is required");
        }
    }

    /**
     * Liveness check — the guest session must still be valid. Runs after the
     * idempotency short-circuit so a completed purchase can replay without a live
     * session.
     *
     * @param guestSessionId the guest session id
     * @throws SessionExpiredException if the session is no longer valid
     */
    private void validateGuestSessionLive(String guestSessionId) {
        if (!sessionManager.validateCredential(guestSessionId)) {
            throw new SessionExpiredException();
        }
    }

    /**
     * Validates that the idempotency key, currency and card details are all
     * present.
     *
     * @param idempotencyKey the dedupe key
     * @param currency       the payment currency
     * @param card           the card details
     * @param userId         the buyer's user id, or {@code null} for a guest (context only)
     * @throws IllegalArgumentException if any required payment input is missing
     */
    private void validatePaymentInput(String idempotencyKey, String currency, CardDetailsDTO card,
            Integer userId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Missing idempotency key");
        }

        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Missing currency");
        }

        if (card == null || card.cardNumber() == null || card.cardNumber().isBlank()) {
            throw new IllegalArgumentException("Missing card details");
        }
    }

    /**
     * Validates that the active order exists and is in a checkout-able state (not
     * empty, not already bought, etc.).
     *
     * @param order  the active order (may be null)
     * @param userId the buyer's user id, or {@code null} for a guest (context only)
     * @throws EntityNotFoundException         if the order is null
     * @throws InvalidStateTransitionException if the order cannot be checked out
     */
    private void validateOrderForCheckout(ActiveOrder order, Integer userId) {
        if (order == null) {
            throw new EntityNotFoundException("Active order not found");
        }

        if (!order.validateCanCheckout()) {
            throw new InvalidStateTransitionException("Order cannot checkout");
        }
    }

    /**
     * Extracts the distinct event ids from the cart, sorted, to give a consistent
     * lock-acquisition order that prevents deadlocks between concurrent checkouts.
     *
     * @param items the cart line items
     * @return the distinct event ids, ascending
     */
    private List<Integer> extractSortedEventIds(List<CartLineItem> items) {
        return items.stream()
                .map(CartLineItem::geteventId)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Acquires the buyer-operation (read) lock on each event, in the given order.
     *
     * @param eventIds the event ids to lock (pre-sorted for deadlock avoidance)
     */
    private void lockEvents(List<Integer> eventIds) {
        for (Integer eventId : eventIds) {
            eventRepository.lockForBuyerOperation(eventId);
        }
    }

    /**
     * Releases the buyer-operation locks acquired by {@link #lockEvents}, in
     * reverse order. Phase 3 still blocks structural event editing but no longer
     * blocks unrelated buyer operations.
     *
     * @param eventIds the event ids to unlock
     */
    private void unlockEvents(List<Integer> eventIds) {
        for (int i = eventIds.size() - 1; i >= 0; i--) {
            eventRepository.unlockBuyerOperation(eventIds.get(i));
        }
    }

    /**
     * Phase 3 re-check that every event in the cart is still purchasable. SOLD_OUT
     * is allowed (holders of existing reservations must be able to complete their
     * purchase, including the last tickets); other non-ON_SALE states are rejected.
     *
     * @param boughtItems the cart items being purchased
     * @throws EventNotFoundException  if an event no longer exists
     * @throws EventNotOnSaleException if an event is neither ON_SALE nor SOLD_OUT
     */
    private void validateEventsStillOnSale(List<CartLineItem> boughtItems) {
        List<Integer> eventIds = extractSortedEventIds(boughtItems);

        for (Integer eventId : eventIds) {
            Event event = eventRepository.findById(eventId);

            if (event == null) {
                throw new EventNotFoundException("Event not found: " + eventId);
            }

            // SOLD_OUT is allowed: an event that sold out while these tickets were reserved must still
            // let the holders complete their purchase (mirrors Event.validateCanConfirmSale). Rejecting
            // it here blocked the sale of the last tickets of any event.
            if (event.getStatus() != EventStatus.ON_SALE && event.getStatus() != EventStatus.SOLD_OUT) {
                throw new EventNotOnSaleException(
                        eventId, "" + event.getStatus());
            }
        }
    }

    /**
     * Prices every cart line once, at a single pricing time, so the total is
     * consistent and any quantity-dependent pricing is computed against the whole
     * purchase.
     *
     * @param boughtItems the cart items being purchased
     * @return the items paired with their computed per-ticket final price
     * @throws EventNotFoundException if an item references a missing event
     */
    private List<PricedCartLine> priceItemsOnce(List<CartLineItem> boughtItems) {
        LocalDateTime pricingTime = LocalDateTime.now();

        Map<Integer, Long> quantityByEvent = boughtItems.stream()
                .collect(Collectors.groupingBy(CartLineItem::geteventId, Collectors.counting()));

        List<PricedCartLine> pricedItems = new ArrayList<>();

        for (CartLineItem item : boughtItems) {
            Event event = eventRepository.findById(item.geteventId());
            if (event == null) {
                throw new EventNotFoundException("Event not found: " + item.geteventId());
            }

            int eventQuantity = quantityByEvent.get(item.geteventId()).intValue();

            double finalPrice = event.calculatePriceforoneticket(
                    eventQuantity,
                    item.getPriceAtReservation(),
                    pricingTime);

            pricedItems.add(new PricedCartLine(item, finalPrice));
        }

        return pricedItems;
    }

    /**
     * @param pricedItems the priced cart lines
     * @return the sum of their final prices — the amount to charge
     */
    private double sumPrices(List<PricedCartLine> pricedItems) {
        return pricedItems.stream()
                .mapToDouble(PricedCartLine::finalPrice)
                .sum();
    }

    /**
     * Charges the payment gateway for the given total.
     *
     * @param buyerUserId    the member's id, or {@code null} for a guest
     * @param buyerEmail     the guest's email, or {@code null} for a member
     * @param totalPrice     the amount to charge
     * @param idempotencyKey the dedupe key passed through to the gateway
     * @param currency       the payment currency
     * @param card           the card details
     * @return the gateway's payment result
     * @throws PaymentGatewayException if the gateway declines or fails
     */
    private PaymentResultDTO chargePayment(Integer buyerUserId, String buyerEmail, double totalPrice,
            String idempotencyKey, String currency, CardDetailsDTO card) {
        PaymentRequestDTO requestToPay = new PaymentRequestDTO(
                idempotencyKey,
                totalPrice,
                currency,
                card,
                buyerUserId,
                buyerEmail);

        return paymentGateway.charge(requestToPay);
    }

    /**
     * Validates the gateway's payment result against expectations (non-null, valid
     * transaction id, gateway name and charge time present, currency and amount
     * matching).
     *
     * @param paymentResult   the gateway result
     * @param expectedAmount  the amount that should have been charged
     * @param expectedCurrency the currency that should have been charged
     * @throws PaymentGatewayException if any field is missing or mismatched
     */
    private void validatePaymentResult(PaymentResultDTO paymentResult, double expectedAmount, String expectedCurrency) {
        if (paymentResult == null) {
            throw new PaymentGatewayException("payment gateway returned null result");
        }

        if (paymentResult.paymentTransactionId() <= 0) {
            throw new PaymentGatewayException("payment transaction id must be positive");
        }

        if (paymentResult.gatewayName() == null || paymentResult.gatewayName().isBlank()) {
            throw new PaymentGatewayException("gateway name is missing");
        }

        if (paymentResult.chargedAt() == null) {
            throw new PaymentGatewayException("payment charge time is missing");
        }

        if (paymentResult.currency() == null || !paymentResult.currency().equalsIgnoreCase(expectedCurrency)) {
            throw new PaymentGatewayException("payment currency mismatch");
        }

        if (Math.abs(paymentResult.chargedAmount() - expectedAmount) > 0.0001) {
            throw new PaymentGatewayException("payment amount mismatch");
        }
    }

    /**
     * Issues tickets via the external ticket issuer for the purchased items.
     *
     * @param buyerUserId the member's id, or {@code null} for a guest
     * @param buyerEmail  the guest's email, or {@code null} for a member
     * @param boughtItems the cart items being purchased
     * @return the issuer's result (transaction id + barcodes)
     * @throws EventNotFoundException        if an item references a missing event
     * @throws TicketIssuanceFailedException if issuance fails
     */
    private IssuanceResultDTO issueTickets(Integer buyerUserId, String buyerEmail, List<CartLineItem> boughtItems) {
        List<IssuanceRequestDTO.TicketIssuanceItemDTO> issuanceItems = boughtItems.stream()
                .map(item -> {
                    Event event = eventRepository.findById(item.geteventId());
                    if (event == null) {
                        throw new EventNotFoundException("Event not found: " + item.geteventId());
                    }

                    return new IssuanceRequestDTO.TicketIssuanceItemDTO(
                            item.geteventId(),
                            event.getName(),
                            item.getzoneId(),
                            item.getSeatNumber());
                })
                .toList();

        IssuanceRequestDTO issuanceRequest = new IssuanceRequestDTO(
                buyerUserId,
                buyerEmail,
                issuanceItems);

        return ticketIssuer.issue(issuanceRequest);
    }

    /**
     * Validates the issuer's result: non-null, valid transaction id, issuer name
     * and time present, one barcode per purchased item, and each barcode carrying a
     * positive ticket id and non-blank value.
     *
     * @param issuanceResult the issuer result
     * @param boughtItems    the items that were submitted for issuance
     * @param userId         the member's id, or {@code null} for a guest (context only)
     * @throws TicketIssuanceFailedException if any field is missing or counts mismatch
     */
    private void validateIssuanceResult(
            IssuanceResultDTO issuanceResult,
            List<CartLineItem> boughtItems,
            Integer userId) {
        if (issuanceResult == null) {
            throw new TicketIssuanceFailedException("Ticket issuance failed");
        }

        if (issuanceResult.issuanceTransactionId() == null || issuanceResult.issuanceTransactionId().isBlank()) {
            throw new TicketIssuanceFailedException("Ticket issuance transaction id is missing");
        }

        if (issuanceResult.issuerName() == null || issuanceResult.issuerName().isBlank()) {
            throw new TicketIssuanceFailedException("Ticket issuer name is missing");
        }

        if (issuanceResult.issuedAt() == null) {
            throw new TicketIssuanceFailedException("Ticket issuance time is missing");
        }

        if (issuanceResult.barcodes() == null || issuanceResult.barcodes().isEmpty()) {
            throw new TicketIssuanceFailedException("Ticket issuance returned no barcodes");
        }

        if (issuanceResult.barcodes().size() != boughtItems.size()) {
            throw new TicketIssuanceFailedException("Ticket issuance count mismatch");
        }

        for (var barcode : issuanceResult.barcodes()) {
            if (barcode.ticketId() <= 0) {
                throw new TicketIssuanceFailedException("Issued ticket id must be positive");
            }

            if (barcode.barcodeValue() == null || barcode.barcodeValue().isBlank()) {
                throw new TicketIssuanceFailedException("Issued barcode value must not be blank");
            }
        }
    }

    /**
     * Phase 3 re-check that the order still exists and is CHECKOUT_IN_PROGRESS.
     *
     * @param order the order re-fetched in Phase 3
     * @throws EntityNotFoundException         if the order disappeared
     * @throws InvalidStateTransitionException if it is no longer in checkout
     */
    private void validateOrderStillInCheckout(ActiveOrder order) {
        if (order == null) {
            throw new EntityNotFoundException("Active order disappeared during checkout");
        }

        if (!order.isCheckoutInProgress()) {
            throw new InvalidStateTransitionException("Active order is no longer in checkout progress");
        }
    }

    /**
     * Phase 3 guard that the cart hasn't changed since the Phase 1 snapshot,
     * comparing order-independent line signatures.
     *
     * @param order         the order re-fetched in Phase 3
     * @param snapshotItems the items snapshotted in Phase 1
     * @throws ConcurrentReservationException if the cart changed during checkout
     */
    private void validateCheckoutSnapshotStillMatches(ActiveOrder order, List<CartLineItem> snapshotItems) {
        List<String> currentSignature = cartLineSignature(order.getItems());
        List<String> snapshotSignature = cartLineSignature(snapshotItems);

        if (!currentSignature.equals(snapshotSignature)) {
            throw new ConcurrentReservationException("Active order changed during checkout");
        }
    }

    /**
     * @param items the cart line items
     * @return the sorted per-line signatures, for order-independent comparison
     */
    private List<String> cartLineSignature(List<CartLineItem> items) {
        return items.stream()
                .map(this::cartLineSignature)
                .sorted()
                .toList();
    }

    /**
     * @param item a cart line item
     * @return a stable signature of (event, zone, seat, reservation price)
     */
    private String cartLineSignature(CartLineItem item) {
        return item.geteventId()
                + "|"
                + item.getzoneId()
                + "|"
                + String.valueOf(item.getSeatNumber())
                + "|"
                + item.getPriceAtReservation();
    }

    /**
     * Phase 3 ownership re-check (read-only): verifies the events/zones exist, that
     * seated reservations are still RESERVED and held by this order key, and that
     * standing zones still hold enough reserved inventory — i.e. the reservations
     * weren't stolen by expiry/cleanup while locks were released in Phase 2.
     *
     * @param boughtItems the cart items being purchased
     * @param orderKey    this checkout's order key (ownership token)
     * @throws EventNotFoundException         if an event no longer exists
     * @throws InsufficientInventoryException if reserved inventory is insufficient or seat data is inconsistent
     * @throws ConcurrentReservationException if a seat is no longer RESERVED or is held by another order
     */
    private void validateCanConfirmInventorySale(List<CartLineItem> boughtItems, String orderKey) {
        Map<Integer, Map<Integer, List<CartLineItem>>> grouped = groupItemsByEventAndZone(boughtItems);

        for (Map.Entry<Integer, Map<Integer, List<CartLineItem>>> eventEntry : grouped.entrySet()) {
            Event event = eventRepository.findById(eventEntry.getKey());
            if (event == null) {
                throw new EventNotFoundException("Event not found: " + eventEntry.getKey());
            }

            for (Map.Entry<Integer, List<CartLineItem>> zoneEntry : eventEntry.getValue().entrySet()) {
                int zoneId = zoneEntry.getKey();
                List<CartLineItem> zoneItems = zoneEntry.getValue();

                InventoryZone zone = event.getVenueMap().getZone(zoneId);

                List<String> seatNumbers = extractSeatNumbers(zoneItems);

                if (seatNumbers.isEmpty()) {
                    if (zone.isSeated()) {
                        throw new InsufficientInventoryException("Seated cart item is missing seat numbers");
                    }

                    if (zone.getReservedAmount() < zoneItems.size()) {
                        throw new InsufficientInventoryException(
                                "Not enough reserved standing tickets to confirm sale");
                    }
                } else {
                    if (zone.isStanding()) {
                        throw new InsufficientInventoryException("Standing cart item cannot contain seat numbers");
                    }

                    if (!(zone instanceof SeatedZone seatedZone)) {
                        throw new InsufficientInventoryException("Zone is not a seated zone");
                    }

                    for (String seatNumber : seatNumbers) {
                        if (seatedZone.getSeatStatus(seatNumber) != SeatStatus.RESERVED) {
                            throw new ConcurrentReservationException(
                                    "Seat " + seatNumber + " is no longer RESERVED — reservation may have expired");
                        }
                        // Ownership check: ensure this checkout's order still holds the seat.
                        // Skip when the seat was reserved without an explicit order key (e.g. test
                        // setups that call seatedZone.reserve() directly without an orderKey — those
                        // reservations are stored under the anonymous sentinel and carry no ownership).
                        Seat seat = seatedZone.getSeatByLabel(seatNumber); // ? Note: ownership check below.
                        String seatOwner = seat.getReservedByOrderKey();
                        if (orderKey != null && !orderKey.equals(seatOwner)) {
                            throw new ConcurrentReservationException(
                                    "Seat " + seatNumber + " is held by a different order — cannot confirm sale");
                        }
                    }
                }
            }
        }
    }

    /**
     * Commits the RESERVED→SOLD transition for each zone (passing the order key so
     * each zone re-verifies ownership). Confirming across multiple events/zones is
     * not atomic, so each confirmed unit is tracked and a mid-loop failure
     * compensates the already-confirmed units back to AVAILABLE (C3) — never
     * leaving a partial SOLD end-state.
     *
     * @param boughtItems the cart items being purchased
     * @param orderKey    this checkout's order key (ownership token)
     * @throws RuntimeException if a confirmation fails (after compensating prior units)
     */
    private void confirmInventorySale(List<CartLineItem> boughtItems, String orderKey) {
        Map<Integer, Map<Integer, List<CartLineItem>>> grouped = groupItemsByEventAndZone(boughtItems);

        // Confirming across multiple events/zones is not a single atomic step. Track each confirmed unit
        // so a mid-loop failure can be compensated (SOLD -> AVAILABLE): we must never leave a partial SOLD
        // end-state, so the normal !inventorySaleConfirmed rollback + refund stays correct (C3).
        List<ConfirmedUnit> confirmed = new ArrayList<>();
        try {
            for (Map.Entry<Integer, Map<Integer, List<CartLineItem>>> eventEntry : grouped.entrySet()) {
                Event event = eventRepository.findById(eventEntry.getKey());

                for (Map.Entry<Integer, List<CartLineItem>> zoneEntry : eventEntry.getValue().entrySet()) {
                    int zoneId = zoneEntry.getKey();
                    List<CartLineItem> zoneItems = zoneEntry.getValue();

                    List<String> seatNumbers = extractSeatNumbers(zoneItems);
                    InventorySelection selection = seatNumbers.isEmpty()
                            ? InventorySelection.standing(zoneItems.size(), orderKey)
                            : InventorySelection.seated(seatNumbers, orderKey);

                    event.confirmInventorySale(zoneId, selection);
                    confirmed.add(new ConfirmedUnit(event, zoneId, selection));
                }

                eventRepository.save(event);
            }
        } catch (RuntimeException confirmFailure) {
            compensateConfirmedSales(confirmed);
            throw confirmFailure;
        }
    }

    /**
     * Best-effort reversal (SOLD → AVAILABLE) of units already confirmed when a
     * multi-event confirm fails partway, so no inventory is stranded SOLD. Runs
     * under the Phase 3 event locks; failures are logged, not propagated (already
     * on the failure path).
     *
     * @param confirmed the units confirmed before the failure, to reverse
     */
    private void compensateConfirmedSales(List<ConfirmedUnit> confirmed) {
        for (int i = confirmed.size() - 1; i >= 0; i--) {
            ConfirmedUnit unit = confirmed.get(i);
            try {
                unit.event().returnSoldToStock(unit.zoneId(), unit.selection());
                eventRepository.save(unit.event());
            } catch (RuntimeException compensationFailure) {
                log.error("Failed to compensate a confirmed sale during checkout rollback. zoneId={}",
                        unit.zoneId(), compensationFailure);
            }
        }
    }

    /**
     * Consumes the cart after a committed sale ({@code buy()} empties it, then it
     * is deleted). Best-effort — a cleanup failure here must not turn a committed
     * purchase into a checkout failure (C2).
     *
     * @param order the order to consume
     */
    private void finalizeConsumedOrder(ActiveOrder order) {
        try {
            order.buy();
            activeOrderRepository.delete(order);
        } catch (RuntimeException cleanupFailure) {
            log.warn("Sale committed but consumed-order cleanup failed for orderKey={}",
                    order.getOrderKey(), cleanupFailure);
        }
    }

    /**
     * Groups cart line items by event id, then by zone id, for the inventory
     * validation/confirmation steps.
     *
     * @param items the cart line items
     * @return a nested map: eventId → (zoneId → items)
     */
    private Map<Integer, Map<Integer, List<CartLineItem>>> groupItemsByEventAndZone(List<CartLineItem> items) {
        return items.stream()
                .collect(Collectors.groupingBy(
                        CartLineItem::geteventId,
                        Collectors.groupingBy(CartLineItem::getzoneId)));
    }

    /**
     * @param zoneItems the cart items for a single zone
     * @return the non-null seat numbers (empty for a standing zone)
     */
    private List<String> extractSeatNumbers(List<CartLineItem> zoneItems) {
        return zoneItems.stream()
                .map(CartLineItem::getSeatNumber)
                .filter(seatNumber -> seatNumber != null)
                .toList();
    }

    /**
     * Persists one issued {@code Ticket} per priced cart line (marked ISSUED with
     * its barcode) and builds the matching receipt lines. Barcodes are matched to
     * lines by index.
     *
     * @param holderUserId   the member holder's id, or {@code null} for a guest
     * @param orderReceiptId the receipt id the tickets belong to
     * @param pricedItems    the priced cart lines
     * @param issuanceResult the issuer result supplying ticket ids/barcodes
     * @return the receipt lines for the order receipt
     */
    private List<ReceiptLine> saveTicketsAndBuildReceiptLines(
            Integer holderUserId,
            int orderReceiptId,
            List<PricedCartLine> pricedItems,
            IssuanceResultDTO issuanceResult) {
        List<ReceiptLine> receiptLines = new ArrayList<>();

        for (int i = 0; i < pricedItems.size(); i++) {
            PricedCartLine pricedItem = pricedItems.get(i);
            CartLineItem item = pricedItem.item();
            var barcode = issuanceResult.barcodes().get(i);

            Ticket ticket = new Ticket(
                    item.geteventId(),
                    item.getzoneId(),
                    orderReceiptId,
                    item.getSeatNumber(),
                    pricedItem.finalPrice(),
                    barcode.ticketId(),
                    barcode.barcodeValue());

            if (holderUserId != null) {
                ticket.setHolderUserId(holderUserId);
            }

            ticket.markIssued(barcode.barcodeValue());
            ticket.checkInvariants();

            ticketRepository.save(ticket);

            ReceiptLine line = new ReceiptLine(
                    barcode.ticketId(),
                    pricedItem.finalPrice(),
                    item.geteventId(),
                    item.getzoneId(),
                    item.getSeatNumber(),
                    LocalDateTime.now());

            line.checkInvariants();
            receiptLines.add(line);
        }

        return receiptLines;
    }

    /**
     * Creates and persists the member's order receipt (with the payment + issuance
     * transactions).
     *
     * @param userId         the member's id
     * @param receiptId      the receipt id
     * @param totalPrice     the order total
     * @param receiptLines   the per-ticket receipt lines
     * @param paymentResult  the gateway payment result
     * @param issuanceResult the issuer result
     */
    private void saveMemberReceipt(
            int userId,
            int receiptId,
            double totalPrice,
            List<ReceiptLine> receiptLines,
            PaymentResultDTO paymentResult,
            IssuanceResultDTO issuanceResult) {
        OrderReceipt receipt = OrderReceipt.forMember(
                receiptId,
                userId,
                totalPrice,
                receiptLines,
                buildPurchaseTransactions(paymentResult, issuanceResult));

        orderReceiptRepository.save(receipt);
    }

    /**
     * Creates and persists the guest's order receipt (with the payment + issuance
     * transactions).
     *
     * @param guestEmail     the guest's email
     * @param guestSessionId the guest's session id
     * @param receiptId      the receipt id
     * @param totalPrice     the order total
     * @param receiptLines   the per-ticket receipt lines
     * @param paymentResult  the gateway payment result
     * @param issuanceResult the issuer result
     */
    private void saveGuestReceipt(
            String guestEmail,
            String guestSessionId,
            int receiptId,
            double totalPrice,
            List<ReceiptLine> receiptLines,
            PaymentResultDTO paymentResult,
            IssuanceResultDTO issuanceResult) {
        OrderReceipt receipt = OrderReceipt.forGuest(
                guestEmail,
                guestSessionId,
                receiptId,
                totalPrice,
                receiptLines,
                buildPurchaseTransactions(paymentResult, issuanceResult));

        orderReceiptRepository.save(receipt);
    }

    /**
     * Builds the receipt's transaction records — one for the payment charge and one
     * for the ticket issuance.
     *
     * @param paymentResult  the gateway payment result
     * @param issuanceResult the issuer result
     * @return the payment + issuance transaction records
     */
    private List<TransactionRecord> buildPurchaseTransactions(
            PaymentResultDTO paymentResult,
            IssuanceResultDTO issuanceResult) {
        List<TransactionRecord> transactions = new ArrayList<>();

        transactions.add(TransactionRecord.paymentCharge(
                paymentResult.paymentTransactionId(),
                paymentResult.gatewayName(),
                paymentResult.chargedAmount(),
                paymentResult.currency(),
                paymentResult.chargedAt()));

        transactions.add(TransactionRecord.ticketIssuance(
                issuanceResult.issuanceTransactionId(),
                issuanceResult.issuerName(),
                issuanceResult.issuedAt()));

        return transactions;
    }

    /**
     * Builds the result DTO returned to the caller (total, receipt id, payment
     * transaction id, issued tickets).
     *
     * @param totalPrice     the order total
     * @param orderReceiptId the receipt id
     * @param paymentResult  the gateway payment result
     * @param issuanceResult the issuer result
     * @return the checkout result DTO
     */
    private CheckoutResultDTO buildCheckoutResult(
            double totalPrice,
            int orderReceiptId,
            PaymentResultDTO paymentResult,
            IssuanceResultDTO issuanceResult) {
        return new CheckoutResultDTO(
                totalPrice,
                orderReceiptId,
                paymentResult.paymentTransactionId(),
                issuanceResult.barcodes()
                        .stream()
                        .map(barcode -> new CheckoutResultDTO.IssuedTicketDTO(
                                barcode.ticketId(), barcode.barcodeValue()))
                        .toList());
    }

    /**
     * Notifies a member that their purchase completed, with the total and the
     * purchased ticket ids.
     *
     * @param userId       the member to notify
     * @param totalPrice   the order total
     * @param receiptLines the receipt lines (source of the ticket ids)
     */
    private void notifyPurchaseCompleted(int userId, double totalPrice, List<ReceiptLine> receiptLines) {
        notificationService.notifyPurchaseCompleted(
                userId,
                totalPrice,
                receiptLines.stream()
                        .map(ReceiptLine::getTicketId)
                        .toList());
    }

    /**
     * Centralizes member checkout-failure handling: logs context, rolls back
     * reserved inventory and the order status atomically (skipped once the sale
     * committed), refunds the charge if the sale did <em>not</em> commit, and
     * notifies the member of the failure.
     *
     * @param order                  the order (possibly stale; re-fetched under lock)
     * @param userId                 the member's id
     * @param orderLockKey           the order lock key, or {@code null} if locking never happened
     * @param paymentResult          the charge result, or {@code null} if not charged
     * @param totalPrice             the amount charged (for the refund)
     * @param inventorySaleConfirmed whether the RESERVED→SOLD commit succeeded
     * @param originalFailure        the failure being handled (logged, not rethrown here)
     */
    private void handleCheckoutFailure(
            ActiveOrder order,
            int userId,
            String orderLockKey,
            PaymentResultDTO paymentResult,
            double totalPrice,
            boolean inventorySaleConfirmed,
            Exception originalFailure) {
        log.error(
                "Checkout failed. userId={}, totalPrice={}, paymentDone={}, inventorySaleConfirmed={}",
                userId,
                totalPrice,
                paymentResult != null,
                inventorySaleConfirmed,
                originalFailure);

        // Reset the CHECKOUT_IN_PROGRESS status, return the reserved inventory to stock, and clear the cart
        // atomically under one lock scope. The guard inside the helper skips the rollback once the sale is
        // confirmed SOLD (a release would throw and the order is still CHECKOUT_IN_PROGRESS). Mirrors
        // handleGuestCheckoutFailure.
        rollbackReservedInventoryAtomically(orderLockKey, order, inventorySaleConfirmed);

        if (inventorySaleConfirmed) {
            log.error(
                    "Checkout failed after inventory was confirmed as SOLD. Manual recovery may be required. userId={}",
                    userId);
        }

        // Refund only if the sale did NOT commit. Once inventory is confirmed SOLD the purchase is final;
        // refunding here would leave the buyer holding tickets they were also refunded for (C2).
        if (paymentResult != null && !inventorySaleConfirmed) {
            safelyRefundPayment(paymentResult, totalPrice);
        }

        if (userId > 0) {
            try {
                notificationService.notifyPurchaseFailed(userId, "Checkout failed.");
            } catch (RuntimeException notificationFailure) {
                log.warn("Checkout failed and failure-notification also failed for userId={}", userId,
                        notificationFailure);
            }
        }
    }

    /**
     * Guest counterpart of {@link #handleCheckoutFailure}: logs context, rolls back
     * reserved inventory and order status atomically, and refunds the charge if the
     * sale did not commit. No failure notification is sent (guests have no user id),
     * but the failure is logged for manual follow-up.
     *
     * @param order                  the order (possibly stale; re-fetched under lock)
     * @param guestSessionId         the guest's session id
     * @param orderLockKey           the order lock key, or {@code null} if locking never happened
     * @param paymentResult          the charge result, or {@code null} if not charged
     * @param totalPrice             the amount charged (for the refund)
     * @param inventorySaleConfirmed whether the RESERVED→SOLD commit succeeded
     * @param originalFailure        the failure being handled (logged, not rethrown here)
     */
    private void handleGuestCheckoutFailure(
            ActiveOrder order,
            String guestSessionId,
            String orderLockKey,
            PaymentResultDTO paymentResult,
            double totalPrice,
            boolean inventorySaleConfirmed,
            Exception originalFailure) {
        log.error(
                "Guest checkout failed. guestSessionId={}, totalPrice={}, paymentDone={}, inventorySaleConfirmed={}",
                guestSessionId,
                totalPrice,
                paymentResult != null,
                inventorySaleConfirmed,
                originalFailure);

        // Roll back inventory + cart + status atomically under one lock scope (see handleCheckoutFailure).
        rollbackReservedInventoryAtomically(orderLockKey, order, inventorySaleConfirmed);

        if (inventorySaleConfirmed) {
            log.error(
                    "Guest checkout failed after inventory was confirmed as SOLD. Manual recovery may be required. guestSessionId={}",
                    guestSessionId);
        }

        // Refund only if the sale did NOT commit. Once inventory is confirmed SOLD the purchase is final;
        // refunding here would leave the buyer holding tickets they were also refunded for (C2).
        if (paymentResult != null && !inventorySaleConfirmed) {
            safelyRefundPayment(paymentResult, totalPrice);
        }
    }

    /**
     * Atomic checkout-failure rollback: under a single order-write-lock +
     * event-read-lock scope, resets CHECKOUT_IN_PROGRESS, returns reserved
     * inventory to stock, and clears the cart. Folding these into one lock scope
     * closes the window where a concurrent op could interleave, and lets
     * {@code eventRepository.save} run under the event lock it requires. Skipped
     * once the sale committed (inventory is SOLD) or before Phase 1 acquired the
     * order. Reentrant-safe: in a Phase 3 failure the main flow already holds these
     * locks, so re-acquiring just bumps the hold count.
     *
     * @param orderLockKey            the order lock key, or {@code null} if none
     * @param fallbackOrder           the main-flow order reference, used only if the
     *                               re-fetch under lock returns null
     * @param inventorySaleConfirmed whether the sale already committed (skip if so)
     */
    private void rollbackReservedInventoryAtomically(
            String orderLockKey,
            ActiveOrder fallbackOrder,
            boolean inventorySaleConfirmed) {
        // Skip when the sale already committed (inventory is SOLD — a release would throw while the order is
        // still CHECKOUT_IN_PROGRESS) or there is no key to lock by (failure before Phase 1 acquired the order,
        // so nothing was reserved for this checkout to roll back). A Phase-1 validation failure still rolls
        // back: the status reset below is guarded by isCheckoutInProgress(), so it is simply skipped while the
        // reserved tickets are still returned to stock and the cart is cleared.
        if (inventorySaleConfirmed || orderLockKey == null) {
            return;
        }

        activeOrderRepository.lockForUpdate(orderLockKey);
        List<Integer> lockedEventIds = List.of();
        try {
            // Re-fetch under the lock; never trust the possibly-stale main-flow reference.
            ActiveOrder order = getOrderByLockKey(orderLockKey);
            if (order == null) {
                order = fallbackOrder;
            }
            if (order == null) {
                return;
            }

            // Lock the order's events (sorted, for consistent ordering) so releaseInventory + save are legal.
            lockedEventIds = extractSortedEventIds(order.getItems());
            lockEvents(lockedEventIds);

            // Reset status FIRST so the order becomes modifiable (clear() refuses while CHECKOUT_IN_PROGRESS),
            // then return reserved inventory to stock and clear the cart — all under the held locks.
            if (order.isCheckoutInProgress()) {
                order.cancelCheckoutInProgress();
            }
            returnTicketsToStock(order);
        } catch (RuntimeException rollbackFailure) {
            // Already in the failure path — log, never mask the original checkout failure.
            log.error("Atomic checkout rollback failed for orderLockKey={}", orderLockKey, rollbackFailure);
        } finally {
            unlockEvents(lockedEventIds);
            activeOrderRepository.unlock(orderLockKey);
        }
    }

    /**
     * Resolves the active order from a lock key (decoding the {@code user:} /
     * {@code sess:} prefix back to a member or guest lookup).
     *
     * @param orderLockKey the order lock key
     * @return the active order, or {@code null} if a guest order is absent
     * @throws IllegalArgumentException if the key prefix is unrecognized
     */
    private ActiveOrder getOrderByLockKey(String orderLockKey) {
        if (orderLockKey.startsWith("user:")) {
            int userId = Integer.parseInt(orderLockKey.substring("user:".length()));
            return activeOrderRepository.getByUserId(userId);
        }

        if (orderLockKey.startsWith("sess:")) {
            String sessionId = orderLockKey.substring("sess:".length());
            return activeOrderRepository.getBySessionId(sessionId).orElse(null);
        }

        throw new IllegalArgumentException("Unknown order lock key format: " + orderLockKey);
    }

    /**
     * Returns the order's still-reserved inventory to stock, releasing each
     * (event, zone) independently so one un-releasable zone never aborts the rest,
     * then clears the cart. Assumes the inventory is RESERVED (not yet SOLD).
     *
     * @param order the order whose reservations to release
     */
    private void returnTicketsToStock(ActiveOrder order) {
        List<CartLineItem> returnToStock = order.getItems();
        String orderKey = order.getOrderKey();

        Map<Integer, Map<Integer, List<CartLineItem>>> grouped = groupItemsByEventAndZone(returnToStock);

        for (Map.Entry<Integer, Map<Integer, List<CartLineItem>>> eventEntry : grouped.entrySet()) {
            int eventId = eventEntry.getKey();
            Event event = eventRepository.findById(eventId);

            if (event == null) {
                continue;
            }

            // Release each (event, zone) independently: a single zone that can no longer be
            // released (e.g. its reservation was already confirmed/expired) must not abort
            // the
            // rollback for the remaining zones and events. We log and continue, then save
            // the
            // event if anything in it was actually released.
            boolean anyReleased = false;
            for (Map.Entry<Integer, List<CartLineItem>> zoneEntry : eventEntry.getValue().entrySet()) {
                int zoneId = zoneEntry.getKey();
                List<CartLineItem> zoneItems = zoneEntry.getValue();

                List<String> seatNumbers = extractSeatNumbers(zoneItems);

                try {
                    if (seatNumbers.isEmpty()) {
                        event.releaseInventory(zoneId, InventorySelection.standing(zoneItems.size(), orderKey));
                    } else {
                        event.releaseInventory(zoneId, InventorySelection.seated(seatNumbers, orderKey));
                    }
                    anyReleased = true;
                } catch (RuntimeException zoneReleaseFailure) {
                    log.warn(
                            "Could not release inventory during checkout rollback. eventId={}, zoneId={}, orderKey={}",
                            eventId, zoneId, orderKey, zoneReleaseFailure);
                }
            }

            if (anyReleased) {
                eventRepository.save(event);
            }
        }

        safelyClearCart(order);
    }

    /**
     * Clears and persists the cart after a rollback, swallowing any failure so it
     * never masks the original checkout failure.
     *
     * @param order the order whose cart to clear
     */
    private void safelyClearCart(ActiveOrder order) {
        try {
            order.clear();
            activeOrderRepository.save(order);
        } catch (RuntimeException clearFailure) {
            log.warn("Could not clear cart during checkout rollback for orderKey={}", order.getOrderKey(),
                    clearFailure);
        }
    }

    /**
     * Refunds a charge during checkout rollback, swallowing any gateway failure so
     * it never masks the original checkout failure (it is logged for follow-up).
     *
     * @param paymentResult the charge to refund
     * @param totalPrice    the amount to refund
     */
    private void safelyRefundPayment(PaymentResultDTO paymentResult, double totalPrice) {
        try {
            paymentGateway.refund(paymentResult.paymentTransactionId(), totalPrice);
        } catch (RuntimeException refundFailure) {
            log.error(
                    "Refund failed after checkout failure. transactionId={}, amount={}",
                    paymentResult.paymentTransactionId(),
                    totalPrice,
                    refundFailure);
        }
    }

    /**
     * Looks up a completed checkout in the idempotency cache.
     *
     * @param idempotencyKey the dedupe key
     * @param buyerKey       the buyer identity expected to own the key
     * @return the cached result if present and owned by this buyer, otherwise {@code null}
     * @throws IdempotencyConflictException if the key is held by a different buyer
     */
    private CheckoutResultDTO getCachedCheckoutResult(String idempotencyKey, String buyerKey) {
        IdempotencyCacheEntry existing = completedCheckoutsByIdempotencyKey.get(idempotencyKey);

        if (existing == null) {
            return null;
        }

        if (!existing.buyerKey().equals(buyerKey)) {
            throw new IdempotencyConflictException(idempotencyKey);
        }

        return existing.result();
    }

    /**
     * Caches a completed checkout result under the idempotency key (via
     * {@code putIfAbsent}, so a concurrent first write is never overwritten).
     *
     * @param idempotencyKey the dedupe key
     * @param buyerKey       the owning buyer identity
     * @param result         the result to cache
     */
    private void cacheCheckoutResult(String idempotencyKey, String buyerKey, CheckoutResultDTO result) {
        completedCheckoutsByIdempotencyKey.putIfAbsent(
                idempotencyKey,
                new IdempotencyCacheEntry(buyerKey, result));
    }

    /**
     * @param userId the member's id
     * @return the buyer identity key for the idempotency cache
     */
    private String memberBuyerKey(int userId) {
        return "member:" + userId;
    }

    /**
     * @param guestSessionId the guest's session id
     * @param guestEmail     the guest's email
     * @return the buyer identity key for the idempotency cache (session + email)
     */
    private String guestBuyerKey(String guestSessionId, String guestEmail) {
        return "guest:" + guestSessionId + ":" + guestEmail.trim().toLowerCase();
    }

    /**
     * @param userId the member's id
     * @return the order lock key for the member ({@code user:} prefix)
     */
    private String memberOrderLockKey(int userId) {
        return "user:" + userId;
    }

    /**
     * @param guestSessionId the guest's session id
     * @return the order lock key for the guest ({@code sess:} prefix)
     */
    private String guestOrderLockKey(String guestSessionId) {
        return "sess:" + guestSessionId;
    }

    /**
     * Evaluates each event's effective purchase policy (company policy combined
     * with the event policy) against a {@link PurchaseContext} built from the
     * per-event quantity and the buyer.
     *
     * @param boughtItems the cart items being purchased
     * @param userId      the member's id, or {@code null} for a guest (treated as buyer id -1)
     * @param buyerAge    the buyer's age (for age policies), may be {@code null}
     * @throws EventNotFoundException if an item references a missing event
     * @throws com.ticketing.system.Core.Domain.exceptions.PolicyViolationException
     *         if a purchase policy rejects the order
     */
    private void validatePurchasePolicies(List<CartLineItem> boughtItems, Integer userId, Integer buyerAge) {
        Map<Integer, Long> quantityByEvent = boughtItems.stream()
                .collect(Collectors.groupingBy(CartLineItem::geteventId, Collectors.counting()));

        for (Map.Entry<Integer, Long> entry : quantityByEvent.entrySet()) {
            int eventId = entry.getKey();
            int quantity = entry.getValue().intValue();

            Event event = eventRepository.findById(eventId);

            if (event == null) {
                throw new EventNotFoundException(eventId);
            }

            int buyerId;
            if (userId == null) {
                buyerId = -1;
            } else {
                buyerId = userId;
            }

            PurchaseContext context = new PurchaseContext(
                    buyerId,
                    buyerAge,
                    event.getId(),
                    event.getCompanyId(),
                    quantity);

            ProductionCompany company = companyRepository.getCompanyById(event.getCompanyId());
            event.validateEffectivePolicy(company == null ? null : company.getPurchasePolicy(), context);
        }
    }

    /**
     * @param userId the member's id
     * @return the member's age (for age-policy evaluation)
     * @throws UserNotFoundException if the user does not exist
     */
    private Integer getBuyerAgeByUserId(int userId) {
        User user = userRepository.getUserById(userId);

        if (user == null) {
            throw new UserNotFoundException("User not found: " + userId);
        }

        return user.getAge();
    }

    // ---------------------------------------------------------------------------
    // Internal helper records (not part of the public API)
    // ---------------------------------------------------------------------------

    /**
     * A cart line item paired with its computed final price, so pricing is done
     * once and reused when building receipt lines.
     */
    private record PricedCartLine(
            CartLineItem item,
            double finalPrice) {
    }

    /**
     * A single (event, zone, selection) confirmed SOLD during
     * {@link #confirmInventorySale}, kept so a partial-confirm failure can be
     * compensated back to AVAILABLE (C3).
     */
    private record ConfirmedUnit(
            Event event,
            int zoneId,
            InventorySelection selection) {
    }

    /**
     * The value stored in the idempotency cache: the owning buyer key (to detect
     * conflicting reuse by a different buyer) and the checkout result to replay.
     */
    private record IdempotencyCacheEntry(
            String buyerKey,
            CheckoutResultDTO result) {
    }

}
