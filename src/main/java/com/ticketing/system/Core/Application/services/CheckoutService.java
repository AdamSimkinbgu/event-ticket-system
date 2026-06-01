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
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.InventorySelection;
import com.ticketing.system.Core.Domain.events.InventoryZone;
import com.ticketing.system.Core.Domain.events.SeatStatus;
import com.ticketing.system.Core.Domain.events.SeatedZone;
import com.ticketing.system.Core.Domain.exceptions.IdempotencyConflictException;
import com.ticketing.system.Core.Domain.exceptions.PaymentGatewayException;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;
import com.ticketing.system.Core.Domain.orders.ReceiptLine;
import com.ticketing.system.Core.Domain.orders.TransactionRecord;

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

    // In-memory cache for completed checkouts to handle idempotency. Keyed by a combination of buyer identity and idempotency key, since the same idempotency key could be used 
    // by different users (e.g. if they copy-paste it from a confirmation page). In a real implementation this would likely be a distributed cache like Redis with an expiration time.
    private final ConcurrentMap<String, IdempotencyCacheEntry> completedCheckoutsByIdempotencyKey = new ConcurrentHashMap<>();

    // idempotency means that if the same buyer (member or guest) submits the same checkout request (identified by idempotency key) multiple times, only the first one will be processed and the result will be returned for subsequent ones. 
    // This is crucial for preventing duplicate charges and orders if, for example, the user accidentally clicks the "Buy" button twice or if there are network issues causing retries.



    public CheckoutService(
            IActiveOrderRepository activeOrderRepository,
            IEventRepository eventRepository,
            ITicketRepository ticketRepository,
            IOrderReceiptRepository orderReceiptRepository,
            ITicketIssuer ticketIssuer,
            IPaymentGateway paymentGateway,
            INotificationService notificationService,
            ISessionManager sessionManager
    ) {
        this.activeOrderRepository = activeOrderRepository;
        this.eventRepository = eventRepository;
        this.ticketRepository = ticketRepository;
        this.orderReceiptRepository = orderReceiptRepository;
        this.ticketIssuer = ticketIssuer;
        this.paymentGateway = paymentGateway;
        this.notificationService = notificationService;
        this.sessionManager = sessionManager;
    }













    // The checkoutMember and checkoutGuest methods follow a similar flow but have some differences in how they identify the buyer (user ID for members, guest session ID + email for guests) and how they retrieve the active order (by user ID vs. by guest session ID). They both implement the same core steps of validating input, checking the cache for idempotency, locking the order and events, pricing items, processing payment, issuing tickets, confirming inventory sale, saving receipts, and handling errors. The separation into two methods allows us to handle member-specific and guest-specific logic cleanly while still sharing common helper methods for the core checkout steps.
    public CheckoutResultDTO checkoutMember(String token, String idempotencyKey, String currency, String paymentMethodToken) {
        int userId = -1;
        ActiveOrder order = null;
        PaymentResultDTO paymentResult = null;
        double totalPrice = 0.0;
        boolean inventorySaleConfirmed = false;

        String orderLockKey = null;
        List<Integer> lockedEventIds = List.of();

        try {
            userId = authenticateAndGetUserId(token);
            validatePaymentInput(idempotencyKey, currency, paymentMethodToken, userId);

            // We use the user ID as part of the buyer key for caching the checkout result. This allows us to maintain idempotency for member checkouts, ensuring that if the same member submits the same checkout request multiple times, only the first one will be processed and the result will be returned for subsequent ones.
            String buyerKey = memberBuyerKey(userId);

            // Check if there's a cached checkout result for this buyer and idempotency key. If so, return it to ensure idempotency.
            CheckoutResultDTO cached = getCachedCheckoutResult(idempotencyKey, buyerKey);
            if (cached != null) {
                return cached;
            }

            orderLockKey = memberOrderLockKey(userId);
            activeOrderRepository.lockForUpdate(orderLockKey);

            order = activeOrderRepository.getByUserId(userId);
            // validate the order exists and is in a state that allows checkout (e.g. not empty, not already bought, etc.). This ensures that we have a valid order to process and that we don't allow checkouts on orders that are not ready for it. If any of these validations fail, we throw an exception to prevent the checkout from proceeding with an invalid order.
            validateOrderForCheckout(order, userId);

            List<CartLineItem> boughtItems = order.getItems();

            // Extract the unique event IDs from the list of cart line items and sort them to ensure a consistent locking order. This is important for preventing deadlocks when we lock events for update during the checkout process. By always locking events in a consistent order (e.g. by event ID), we can reduce the likelihood of two concurrent checkouts trying to lock the same set of events in different orders and blocking each other indefinitely.
            lockedEventIds = extractSortedEventIds(boughtItems);
            lockEvents(lockedEventIds);

            // Price all items at once before processing payment to ensure that the total price is consistent with what the user saw at checkout and to avoid issues where prices might change between individual item pricing calls. This also allows us to apply any relevant discounts or promotions that depend on the overall purchase (e.g. "buy 2 get 1 free" or "10% off if you buy more than 3 tickets").
            List<PricedCartLine> pricedItems = priceItemsOnce(boughtItems);
            totalPrice = sumPrices(pricedItems);

            // Process the payment through the payment gateway. We pass the total price and other relevant information to the payment gateway, which will handle charging the user's payment method. The idempotency key is also passed to the payment gateway to ensure that if we accidentally call this method multiple times with the same key (e.g. due to retries), the payment gateway will only process it once and return the same result for subsequent calls with the same key.
            paymentResult = chargePayment(
                    userId,
                    null,
                    totalPrice,
                    idempotencyKey,
                    currency,
                    paymentMethodToken);

            // Validate the payment result to ensure that the payment was successful and that the amount charged matches the total price we calculated. If the payment failed or if there is a mismatch in amounts, we throw an exception to prevent the checkout from proceeding and to trigger error handling (e.g. rolling back inventory reservations, etc.).
            validatePaymentResult(paymentResult, totalPrice, currency);

            // Issue the tickets through the ticket issuer. We pass the user ID
            IssuanceResultDTO issuanceResult = issueTickets(userId, null, boughtItems);
            // Validate the issuance result to ensure that the tickets were successfully issued. If the ticket issuance failed, we throw an exception to prevent the checkout from proceeding and to trigger error handling (e.g. rolling back payment, inventory reservations, etc.).
            validateIssuanceResult(issuanceResult, boughtItems, userId);

            // Before confirming the inventory sale, we validate that we can confirm the sale for all items in the order. This may involve checking that the inventory is still available and reserved for this order, and that there are no issues that would prevent us from confirming the sale (e.g. event cancellations, etc.). If any of these validations fail, we throw an exception to prevent the checkout from proceeding and to trigger error handling.
            validateCanConfirmInventorySale(boughtItems);
            // If all validations pass, we confirm the inventory sale for all items in the order. This typically involves updating the inventory status for the reserved items to "sold" and making them unavailable for other customers. This step is crucial for ensuring that the tickets are officially sold to the customer and that they cannot be purchased by someone else.
            confirmInventorySale(boughtItems);
            inventorySaleConfirmed = true;

            int orderReceiptId = orderReceiptRepository.nextId();
            // Save the issued tickets and build the receipt lines for the order receipt. This involves creating records for each ticket that was issued as part of this order and associating them with the order receipt. The receipt lines will contain details about each item purchased, such as the event, seat (if applicable), price, etc., which will be used for generating the receipt that is sent to the customer.
            List<ReceiptLine> receiptLines = saveTicketsAndBuildReceiptLines(
                    userId,
                    orderReceiptId,
                    pricedItems,
                    issuanceResult);
            // Save the order receipt for this purchase, including the total price, receipt lines, payment result, and issuance result. This creates a record of the completed purchase that can be referenced in the future (e.g. for purchase history, refunds, etc.).
            saveMemberReceipt(
                    userId,
                    orderReceiptId,
                    totalPrice,
                    receiptLines,
                    paymentResult,
                    issuanceResult);

            order.buy();
            activeOrderRepository.save(order);

            // Notify the user that the purchase has been completed. This typically involves sending an email or push notification with the receipt details and any other relevant information about the purchase.
            notifyPurchaseCompleted(userId, totalPrice, receiptLines);

            // Build the checkout result DTO to return to the caller, which includes details about the purchase that may be displayed on a confirmation page or used for other post-purchase actions. We also cache this result using the buyer identity and idempotency key so that if the same checkout request is received again, we can return the cached result instead of processing the checkout again.
            CheckoutResultDTO result = buildCheckoutResult(totalPrice, orderReceiptId, paymentResult, issuanceResult);
            // Cache the checkout result for this buyer and idempotency key to ensure that if we receive the same checkout request again, we can return the cached result instead of processing the checkout again. This is crucial for maintaining idempotency and preventing duplicate charges and orders.
            cacheCheckoutResult(idempotencyKey, buyerKey, result);

            return result;

        } catch (Exception e) {
            // Handle any exceptions that occur during the checkout process. This includes rolling back any actions that were taken (e.g. releasing inventory reservations, refunding payments if they were processed, etc.) and logging the error for monitoring and debugging purposes. We also rethrow a generic exception to indicate that the checkout failed, which can be caught by higher-level handlers (e.g. in the controller) to return an appropriate error response to the client.
            handleCheckoutFailure(userId, order, paymentResult, totalPrice, inventorySaleConfirmed, e);
            throw new RuntimeException("Checkout failed", e);
        } finally {
            unlockEvents(lockedEventIds);
            if (orderLockKey != null) {
                activeOrderRepository.unlock(orderLockKey);
            }
        }
    }

    











    // The checkoutMember and checkoutGuest methods follow a similar flow but have some differences in how they identify the buyer (user ID for members, guest session ID + email for guests) and how they retrieve the active order (by user ID vs. by guest session ID). They both implement the same core steps of validating input, checking the cache for idempotency, locking the order and events, pricing items, processing payment, issuing tickets, confirming inventory sale, saving receipts, and handling errors. The separation into two methods allows us to handle member-specific and guest-specific logic cleanly while still sharing common helper methods for the core checkout steps.
    public CheckoutResultDTO checkoutGuest(String guestSessionId, String guestEmail, String idempotencyKey, String currency, String paymentMethodToken) {
        ActiveOrder order = null;
        PaymentResultDTO paymentResult = null;
        double totalPrice = 0.0;
        boolean inventorySaleConfirmed = false;

        String orderLockKey = null;
        List<Integer> lockedEventIds = List.of();

        try {
            validateGuestCheckoutIdentity(guestSessionId, guestEmail);
            // For guest checkout, we don't have a user ID to associate with the payment, so we pass null for the userId parameter in the validatePaymentInput method. The payment gateway should be able to handle this and still process the payment based on the provided payment method token and other details. The idempotency key is still required to ensure that we can handle duplicate checkout attempts correctly, even for guests.
            validatePaymentInput(idempotencyKey, currency, paymentMethodToken, null);

            // We use a combination of guest session ID and email as the buyer key for caching the checkout result. This allows us to maintain idempotency for guest checkouts, ensuring that if the same guest submits the same checkout request multiple times, only the first one will be processed and the result will be returned for subsequent ones.
            String buyerKey = guestBuyerKey(guestSessionId, guestEmail);

            // Check if there's a cached checkout result for this buyer and idempotency key. If so, return it to ensure idempotency.
            CheckoutResultDTO cached = getCachedCheckoutResult(idempotencyKey, buyerKey);
            if (cached != null) {
                return cached;
            }

            orderLockKey = guestOrderLockKey(guestSessionId);
            activeOrderRepository.lockForUpdate(orderLockKey);

            // For guest checkout, we retrieve the active order based on the guest session ID instead of a user ID. We then validate that the order exists and is in a state that allows checkout (e.g. not empty, not already bought, etc.). This ensures that we have a valid order to process and that we don't allow checkouts on orders that are not ready for it. If any of these validations fail, we throw an exception to prevent the checkout from proceeding with an invalid order.
            order = activeOrderRepository.getBySessionId(guestSessionId)
                    .orElseThrow(() -> new IllegalStateException("Active guest order not found"));

            // validate the order exists and is in a state that allows checkout (e.g. not empty, not already bought, etc.). This ensures that we have a valid order to process and that we don't allow checkouts on orders that are not ready for it. If any of these validations fail, we throw an exception to prevent the checkout from proceeding with an invalid order.
            validateOrderForCheckout(order, null);

            List<CartLineItem> boughtItems = order.getItems();

            // 
            lockedEventIds = extractSortedEventIds(boughtItems);
            lockEvents(lockedEventIds);

            List<PricedCartLine> pricedItems = priceItemsOnce(boughtItems);
            totalPrice = sumPrices(pricedItems);

            paymentResult = chargePayment(
                    null,
                    guestEmail,
                    totalPrice,
                    idempotencyKey,
                    currency,
                    paymentMethodToken
            );
            validatePaymentResult(paymentResult, totalPrice, currency);

            IssuanceResultDTO issuanceResult = issueTickets(null, guestEmail, boughtItems);
            validateIssuanceResult(issuanceResult, boughtItems, null);

            validateCanConfirmInventorySale(boughtItems);
            confirmInventorySale(boughtItems);
            inventorySaleConfirmed = true;

            int orderReceiptId = orderReceiptRepository.nextId();

            List<ReceiptLine> receiptLines = saveTicketsAndBuildReceiptLines(
                    null,
                    orderReceiptId,
                    pricedItems,
                    issuanceResult
            );

            saveGuestReceipt(
                    guestEmail,
                    guestSessionId,
                    orderReceiptId,
                    totalPrice,
                    receiptLines,
                    paymentResult,
                    issuanceResult
            );

            order.buy();
            activeOrderRepository.save(order);

            CheckoutResultDTO result = buildCheckoutResult(totalPrice, orderReceiptId, paymentResult, issuanceResult);
            cacheCheckoutResult(idempotencyKey, buyerKey, result);

            return result;

        } catch (Exception e) {
            handleGuestCheckoutFailure(guestSessionId, order, paymentResult, totalPrice, inventorySaleConfirmed, e);
            throw new RuntimeException("Guest checkout failed", e);
        } finally {
            unlockEvents(lockedEventIds);
            if (orderLockKey != null) {
                activeOrderRepository.unlock(orderLockKey);
            }
        }
    }






    // Helper methods for checkout flow steps (authentication, validation, pricing, payment, ticket issuance, inventory confirmation, receipt saving, notifications, caching) and error handling are defined below to keep the main checkout methods clean and focused on the overall flow. These helper methods encapsulate specific pieces of logic and can be reused across both member and guest checkout flows where applicable.


    // We validate member checkout identity by checking that the authentication token is present and valid (i.e. corresponds to an active session in our session manager) and that the user ID can be extracted from the token. This ensures that we can associate the checkout with a specific member for tracking and that we have a valid user ID to process the order. If any of these validations fail, we throw an exception to prevent the checkout from proceeding.
    private int authenticateAndGetUserId(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Missing authentication token");
        }

        if (!sessionManager.validateToken(token)) {
            throw new IllegalStateException("Invalid or expired authentication token");
        }

        int userId = sessionManager.extractUserId(token);
        if (userId <= 0) {
            throw new IllegalStateException("Invalid user id in token");
        }

        return userId;
    }




    // We validate guest checkout identity by checking that the guest session ID is present and valid (i.e. corresponds to an active guest session in our session manager) and that the guest email is present. This ensures that we can associate the checkout with a specific guest session for tracking and that we have an email to send the receipt to. If any of these validations fail, we throw an exception to prevent the checkout from proceeding.
    private void validateGuestCheckoutIdentity(String guestSessionId, String guestEmail) {
        if (guestSessionId == null || guestSessionId.isBlank()) {
            throw new IllegalArgumentException("guestSessionId is required");
        }

        if (!sessionManager.validateCredential(guestSessionId)) {
            throw new IllegalStateException("Invalid or expired guest session");
        }

        if (guestEmail == null || guestEmail.isBlank()) {
            throw new IllegalArgumentException("guestEmail is required");
        }
    }





    // We validate payment input by checking that the idempotency key, currency, and payment method token are all present and valid. The idempotency key is required to ensure that we can handle duplicate checkout attempts correctly. The currency is required to ensure that we know which currency the payment should be processed in. The payment method token is required to have a valid reference to the payment method that the user wants to use for the transaction. If any of these validations fail, we throw an exception to prevent the checkout from proceeding with invalid payment information.
    private void validatePaymentInput(String idempotencyKey, String currency, String paymentMethodToken, Integer userId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Missing idempotency key");
        }

        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Missing currency");
        }

        if (paymentMethodToken == null || paymentMethodToken.isBlank()) {
            throw new IllegalArgumentException("Missing payment method token");
        }
    }




    // We validate the active order for checkout by checking that it exists and that it is in a state that allows checkout (e.g. not empty, not already bought, etc.). This ensures that we have a valid order to process and that we don't allow checkouts on orders that are not ready for it. If any of these validations fail, we throw an exception to prevent the checkout from proceeding with an invalid order.
    private void validateOrderForCheckout(ActiveOrder order, Integer userId) {
        if (order == null) {
            throw new IllegalStateException("Active order not found");
        }

        if (!order.validateCanCheckout()) {
            throw new IllegalStateException("Order cannot checkout");
        }
    }





    // We extract the unique event IDs from the list of cart line items and sort them to ensure a consistent locking order. This is important for preventing deadlocks when we lock events for update during the checkout process. By always locking events in a consistent order (e.g. by event ID), we can reduce the likelihood of two concurrent checkouts trying to lock the same set of events in different orders and blocking each other indefinitely.
    private List<Integer> extractSortedEventIds(List<CartLineItem> items) {
        return items.stream()
                .map(CartLineItem::geteventId)
                .distinct()
                .sorted()
                .toList();
    }




    // We lock the events for update to prevent concurrent modifications to the event's inventory and pricing during the checkout process. This ensures that we have a consistent view of the event's state while we are processing the checkout and that we can safely confirm inventory sale and issue tickets without running into
    private void lockEvents(List<Integer> eventIds) {
        for (Integer eventId : eventIds) {
            eventRepository.lockForUpdate(eventId);
        }
    }




    // We unlock in reverse order of locking as a defensive measure to reduce the likelihood of deadlocks in the database. If two threads are trying to lock the same set of events but in different orders, unlocking in reverse order can help mitigate the risk of them blocking each other indefinitely.
    private void unlockEvents(List<Integer> eventIds) {
        for (int i = eventIds.size() - 1; i >= 0; i--) {
            eventRepository.unlock(eventIds.get(i));
        }
    }





    // We price all items at once before processing payment to ensure that the total price is consistent with what the user saw at checkout and to avoid issues where prices might change between individual item pricing calls. This also allows us to apply any relevant discounts or promotions that depend on the overall purchase (e.g. "buy 2 get 1 free" or "10% off if you buy more than 3 tickets").
    private List<PricedCartLine> priceItemsOnce(List<CartLineItem> boughtItems) {
        LocalDateTime pricingTime = LocalDateTime.now();

        Map<Integer, Long> quantityByEvent = boughtItems.stream()
                .collect(Collectors.groupingBy(CartLineItem::geteventId, Collectors.counting()));

        List<PricedCartLine> pricedItems = new ArrayList<>();

        for (CartLineItem item : boughtItems) {
            Event event = eventRepository.findById(item.geteventId());
            if (event == null) {
                throw new IllegalStateException("Event not found: " + item.geteventId());
            }

            int eventQuantity = quantityByEvent.get(item.geteventId()).intValue();

            double finalPrice = event.calculatePriceforoneticket(
                    eventQuantity,
                    item.getPriceAtReservation(),
                    pricingTime
            );

            pricedItems.add(new PricedCartLine(item, finalPrice));
        }

        return pricedItems;
    }





    // We sum the final prices of all priced cart lines to get the total price for the checkout. This total price is what we will charge the customer and what we will use for the payment validation. By summing the final prices from our pricing logic, we ensure that any discounts or promotions that were applied are reflected in the total amount charged to the customer.
    private double sumPrices(List<PricedCartLine> pricedItems) {
        return pricedItems.stream()
                .mapToDouble(PricedCartLine::finalPrice)
                .sum();
    }







    // We charge the payment using the payment gateway by creating a PaymentRequestDTO with all the necessary information (buyer identity, total price, currency, payment method token, idempotency key) and calling the charge method on the payment gateway. This encapsulates the interaction with the payment gateway and allows us to handle any exceptions or errors that might occur during the payment process in a consistent way. The result from the payment gateway will be validated to ensure that the charge was successful and that the amount and currency match what we expected.
    private PaymentResultDTO chargePayment(Integer buyerUserId, String buyerEmail, double totalPrice,
                                            String idempotencyKey, String currency, String paymentMethodToken) {
        PaymentRequestDTO requestToPay = new PaymentRequestDTO(
                idempotencyKey,
                totalPrice,
                currency,
                paymentMethodToken,
                buyerUserId,
                buyerEmail
        );

        return paymentGateway.charge(requestToPay);
    }







    // We validate the payment result from the payment gateway by checking that it is not null, that it contains a valid payment transaction ID, that the gateway name is present, that the charge time is present, and that the charged amount and currency match what we expected. This ensures that we only proceed with successful payments that match our expected values and that we can handle any discrepancies or errors in the payment result appropriately. If any of these validations fail, we throw an exception to prevent the checkout from proceeding with an invalid payment result.
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







    // We issue tickets using the ticket issuer by creating an IssuanceRequestDTO with all the necessary information (buyer identity, list of items being purchased) and calling the issue method on the ticket issuer. This encapsulates the interaction with the ticket issuer and allows us to handle any exceptions or errors that might occur during the ticket issuance process in a consistent way. The result from the ticket issuer will be validated to ensure that the tickets were issued successfully and that we have all the necessary information (e.g. ticket IDs, barcodes) to proceed with saving receipts and confirming inventory sale.
    private IssuanceResultDTO issueTickets(Integer buyerUserId, String buyerEmail, List<CartLineItem> boughtItems) {
        List<IssuanceRequestDTO.TicketIssuanceItemDTO> issuanceItems = boughtItems.stream()
                .map(item -> {
                    Event event = eventRepository.findById(item.geteventId());
                    if (event == null) {
                        throw new IllegalStateException("Event not found: " + item.geteventId());
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

    





    // We validate the issuance result from the ticket issuer by checking that it is not null, that it contains a valid issuance transaction ID, that the issuer name is present, that the issuance time is present, and that the list of issued barcodes matches the number of items we attempted to purchase. We also validate each issued barcode to ensure that it contains a valid ticket ID and a non-blank barcode value. This ensures that we only proceed with successful ticket issuances that match our expected values and that we can handle any discrepancies or errors in the issuance result appropriately. If any of these validations fail, we throw an exception to prevent the checkout from proceeding with an invalid ticket issuance result.
    private void validateIssuanceResult(
            IssuanceResultDTO issuanceResult,
            List<CartLineItem> boughtItems,
            Integer userId
    ) {
        if (issuanceResult == null) {
            throw new IllegalStateException("Ticket issuance failed");
        }

        if (issuanceResult.issuanceTransactionId() == null || issuanceResult.issuanceTransactionId().isBlank()) {
            throw new IllegalStateException("Ticket issuance transaction id is missing");
        }

        if (issuanceResult.issuerName() == null || issuanceResult.issuerName().isBlank()) {
            throw new IllegalStateException("Ticket issuer name is missing");
        }

        if (issuanceResult.issuedAt() == null) {
            throw new IllegalStateException("Ticket issuance time is missing");
        }

        if (issuanceResult.barcodes() == null || issuanceResult.barcodes().isEmpty()) {
            throw new IllegalStateException("Ticket issuance returned no barcodes");
        }

        if (issuanceResult.barcodes().size() != boughtItems.size()) {
            throw new IllegalStateException("Ticket issuance count mismatch");
        }

        for (var barcode : issuanceResult.barcodes()) {
            if (barcode.ticketId() <= 0) {
                throw new IllegalStateException("Issued ticket id must be positive");
            }

            if (barcode.barcodeValue() == null || barcode.barcodeValue().isBlank()) {
                throw new IllegalStateException("Issued barcode value must not be blank");
            }
        }
    }






    // We validate that we can confirm the inventory sale for the items being purchased by checking that the events and zones exist, that the seat numbers (if applicable) are valid and reserved, and that there is enough reserved inventory to confirm the sale. This ensures that we can safely confirm the inventory sale without running into issues like trying to sell tickets that are not reserved or trying to sell more tickets than are available in a standing zone. If any of these validations fail, we throw an exception to prevent the checkout from proceeding with an invalid inventory state.
    private void validateCanConfirmInventorySale(List<CartLineItem> boughtItems) {
        Map<Integer, Map<Integer, List<CartLineItem>>> grouped = groupItemsByEventAndZone(boughtItems);

        for (Map.Entry<Integer, Map<Integer, List<CartLineItem>>> eventEntry : grouped.entrySet()) {
            Event event = eventRepository.findById(eventEntry.getKey());
            if (event == null) {
                throw new IllegalStateException("Event not found: " + eventEntry.getKey());
            }

            for (Map.Entry<Integer, List<CartLineItem>> zoneEntry : eventEntry.getValue().entrySet()) {
                int zoneId = zoneEntry.getKey();
                List<CartLineItem> zoneItems = zoneEntry.getValue();

                InventoryZone zone = event.getVenueMap().getZone(zoneId);

                List<String> seatNumbers = extractSeatNumbers(zoneItems);

                if (seatNumbers.isEmpty()) {
                    if (zone.isSeated()) {
                        throw new IllegalStateException("Seated cart item is missing seat numbers");
                    }

                    if (zone.getReservedAmount() < zoneItems.size()) {
                        throw new IllegalStateException("Not enough reserved standing tickets to confirm sale");
                    }
                } else {
                    if (zone.isStanding()) {
                        throw new IllegalStateException("Standing cart item cannot contain seat numbers");
                    }

                    if (!(zone instanceof SeatedZone seatedZone)) {
                        throw new IllegalStateException("Zone is not a seated zone");
                    }

                    for (String seatNumber : seatNumbers) {
                        if (seatedZone.getSeatStatus(seatNumber) != SeatStatus.RESERVED) {
                            throw new IllegalStateException("Seat " + seatNumber + " is not reserved");
                        }
                    }
                }
            }
        }
    }








    // We confirm the inventory sale for the items being purchased by calling the confirmInventorySale method on the event for each zone being purchased. For standing zones, we confirm the sale by specifying the quantity of tickets being sold. For seated zones, we confirm the sale by specifying the list of seat numbers being sold. This updates the event's inventory to reflect that these tickets have been sold and can no longer be reserved or sold to other customers. After confirming the inventory sale, we save the updated event to persist the changes.
    private void confirmInventorySale(List<CartLineItem> boughtItems) {
        Map<Integer, Map<Integer, List<CartLineItem>>> grouped = groupItemsByEventAndZone(boughtItems);

        for (Map.Entry<Integer, Map<Integer, List<CartLineItem>>> eventEntry : grouped.entrySet()) {
            Event event = eventRepository.findById(eventEntry.getKey());

            for (Map.Entry<Integer, List<CartLineItem>> zoneEntry : eventEntry.getValue().entrySet()) {
                int zoneId = zoneEntry.getKey();
                List<CartLineItem> zoneItems = zoneEntry.getValue();

                List<String> seatNumbers = extractSeatNumbers(zoneItems);

                if (seatNumbers.isEmpty()) {
                    event.confirmInventorySale(zoneId, InventorySelection.standing(zoneItems.size()));
                } else {
                    event.confirmInventorySale(zoneId, InventorySelection.seated(seatNumbers));
                }
            }

            eventRepository.save(event);
        }
    }






    // We group the cart line items by event ID and then by zone ID to facilitate the inventory validation and confirmation steps. This allows us to easily access all items for a specific event and zone when we need to check the inventory status or confirm the sale. The resulting data structure is a nested map where the first key is the event ID, the second key is the zone ID, and the value is a list of cart line items for that event and zone.
    private Map<Integer, Map<Integer, List<CartLineItem>>> groupItemsByEventAndZone(List<CartLineItem> items) {
        return items.stream()
                .collect(Collectors.groupingBy(
                        CartLineItem::geteventId,
                        Collectors.groupingBy(CartLineItem::getzoneId)
                ));
    }






    // We extract the seat numbers from a list of cart line items for a specific zone. If the zone is a standing zone, there should be no seat numbers and we will return an empty list. If the zone is a seated zone, we will return the list of seat numbers that are associated with the cart line items. This helper method simplifies the logic in the inventory validation and confirmation steps by providing a clear way to get the seat numbers for a group of items in the same zone.
    private List<String> extractSeatNumbers(List<CartLineItem> zoneItems) {
        return zoneItems.stream()
                .map(CartLineItem::getSeatNumber)
                .filter(seatNumber -> seatNumber != null)
                .toList();
    }








    // We save the issued tickets to the database and build the receipt lines for the order receipt. For each priced cart line, we create a corresponding Ticket entity with the information from the cart line and the issuance result (e.g. ticket ID, barcode). We then save each ticket to the database and create a ReceiptLine for it that will be included in the order receipt. This method encapsulates the logic for persisting the issued tickets and preparing the data needed for the receipt in one place.
    private List<ReceiptLine> saveTicketsAndBuildReceiptLines(
            Integer holderUserId,
            int orderReceiptId,
            List<PricedCartLine> pricedItems,
            IssuanceResultDTO issuanceResult
    ) {
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

    






    // We save the order receipt for a member by creating an OrderReceipt entity with the member-specific information (user ID) and the details of the purchase (total price, receipt lines, transactions) and then saving it to the database. This method encapsulates the logic for creating and persisting the order receipt for a member in one place. We also build the list of transactions for the receipt by combining the payment transaction and the ticket issuance transaction into a single list that will be included in the receipt.
    private void saveMemberReceipt(
            int userId,
            int receiptId,
            double totalPrice,
            List<ReceiptLine> receiptLines,
            PaymentResultDTO paymentResult,
            IssuanceResultDTO issuanceResult
    ) {
        OrderReceipt receipt = OrderReceipt.forMember(
                receiptId,
                userId,
                totalPrice,
                receiptLines,
                buildPurchaseTransactions(paymentResult, issuanceResult)
        );

        orderReceiptRepository.save(receipt);
    }





    // We save the order receipt for a guest by creating an OrderReceipt entity with the guest-specific information (guest email and session ID) and the details of the purchase (total price, receipt lines, transactions) and then saving it to the database. This method encapsulates the logic for creating and persisting the order receipt for a guest in one place. We also build the list of transactions for the receipt by combining the payment transaction and the ticket issuance transaction into a single list that will be included in the receipt.
    private void saveGuestReceipt(
            String guestEmail,
            String guestSessionId,
            int receiptId,
            double totalPrice,
            List<ReceiptLine> receiptLines,
            PaymentResultDTO paymentResult,
            IssuanceResultDTO issuanceResult
    ) {
        OrderReceipt receipt = OrderReceipt.forGuest(
                guestEmail,
                guestSessionId,
                receiptId,
                totalPrice,
                receiptLines,
                buildPurchaseTransactions(paymentResult, issuanceResult));

        orderReceiptRepository.save(receipt);
    }

    



    // We build the list of transactions for the receipt by creating a TransactionRecord for the payment charge and a TransactionRecord for the ticket issuance. This allows us to have a clear record of the key transactions that occurred during the checkout process, which can be useful for customer service, refunds, or any future audits of the purchase history. By encapsulating this logic in a helper method, we can easily maintain and modify how we record transactions without affecting the main checkout flow.
    private List<TransactionRecord> buildPurchaseTransactions(
            PaymentResultDTO paymentResult,
            IssuanceResultDTO issuanceResult
    ) {
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

    
    // We build the checkout result DTO that will be returned to the caller of the checkout method. This DTO contains the total price, the order receipt ID, the payment transaction ID, and the list of issued ticket IDs. This allows the caller to have all the relevant information about the completed checkout in a single object. By encapsulating this logic in a helper method, we can easily modify what information we include in the checkout result without affecting the main checkout flow.
    private CheckoutResultDTO buildCheckoutResult(
            double totalPrice,
            int orderReceiptId,
            PaymentResultDTO paymentResult,
            IssuanceResultDTO issuanceResult
    ) {
        return new CheckoutResultDTO(
                totalPrice,
                orderReceiptId,
                paymentResult.paymentTransactionId(),
                issuanceResult.barcodes()
                        .stream()
                        .map(barcode -> barcode.ticketId())
                        .toList());
    }

    


    // We notify the user of the completed purchase by sending a notification with the total price and the list of ticket IDs that were purchased. This allows us to provide immediate feedback to the user that their purchase was successful and to give them information about the tickets they bought. By encapsulating this logic in a helper method, we can easily modify how we send notifications or what information we include in the notification without affecting the main checkout flow.
    private void notifyPurchaseCompleted(int userId, double totalPrice, List<ReceiptLine> receiptLines) {
        notificationService.notifyPurchaseCompleted(
                userId,
                totalPrice,
                receiptLines.stream()
                        .map(ReceiptLine::getTicketId)
                        .toList());
    }

    
    // We handle checkout failures by logging the error with relevant information (user ID, total price, whether payment was done, whether inventory sale was confirmed) and then attempting to roll back any changes that were made during the checkout process. If the inventory sale was not confirmed, we try to return the reserved tickets back to stock. If the payment was done, we try to refund the payment. We also send a notification to the user that the purchase failed. This method centralizes all the error handling and rollback logic for member checkouts in one place, making it easier to maintain and ensuring that we consistently handle failures across different failure points in the checkout process.
    private void handleCheckoutFailure(
            int userId,
            ActiveOrder order,
            PaymentResultDTO paymentResult,
            double totalPrice,
            boolean inventorySaleConfirmed,
            Exception originalFailure
    ) {
        log.error(
                "Checkout failed. userId={}, totalPrice={}, paymentDone={}, inventorySaleConfirmed={}",
                userId,
                totalPrice,
                paymentResult != null,
                inventorySaleConfirmed,
                originalFailure);

        if (!inventorySaleConfirmed) {
            safelyReturnTicketsToStock(order);
        } else {
            log.error(
                    "Checkout failed after inventory was confirmed as SOLD. Manual recovery may be required. userId={}",
                    userId);
        }

        if (paymentResult != null) {
            safelyRefundPayment(paymentResult, totalPrice);
        }

        if (userId > 0) {
            notificationService.notifyPurchaseFailed(userId, "Checkout failed.");
        }
    }

    

    // We handle guest checkout failures by logging the error with relevant information (guest session ID, total price, whether payment was done, whether inventory sale was confirmed) and then attempting to roll back any changes that were made during the checkout process. If the inventory sale was not confirmed, we try to return the reserved tickets back to stock. If the payment was done, we try to refund the payment. Since we don't have a user ID for guests, we cannot send a notification about the failure, but we log enough information to allow for manual follow-up if needed. This method centralizes all the error handling and rollback logic for guest checkouts in one place, making it easier to maintain and ensuring that we consistently handle failures across different failure points in the checkout process.
    private void handleGuestCheckoutFailure(
            String guestSessionId,
            ActiveOrder order,
            PaymentResultDTO paymentResult,
            double totalPrice,
            boolean inventorySaleConfirmed,
            Exception originalFailure
    ) {
        log.error(
                "Guest checkout failed. guestSessionId={}, totalPrice={}, paymentDone={}, inventorySaleConfirmed={}",
                guestSessionId,
                totalPrice,
                paymentResult != null,
                inventorySaleConfirmed,
                originalFailure
        );

        if (!inventorySaleConfirmed) {
            safelyReturnTicketsToStock(order);
        } else {
            log.error(
                    "Guest checkout failed after inventory was confirmed as SOLD. Manual recovery may be required. guestSessionId={}",
                    guestSessionId
            );
        }

        if (paymentResult != null) {
            safelyRefundPayment(paymentResult, totalPrice);
        }
    }



    // We attempt to return reserved tickets back to stock during a checkout rollback. This is done as a safety measure in case the checkout process fails before we confirm the inventory sale, allowing us to release the reserved tickets so they can be purchased by other customers. We wrap this in a try-catch block to ensure that if any exceptions occur during the rollback (e.g. database issues, event not found), we log the error but do not let it propagate further since we are already in an error handling flow and we want to avoid masking the original failure with additional exceptions from the rollback process.
    private void safelyReturnTicketsToStock(ActiveOrder order) {
        if (order == null) {
            return;
        }

        try {
            returnTicketsToStock(order);
        } catch (RuntimeException rollbackFailure) {
            log.error("Failed to return reserved tickets to stock during checkout rollback", rollbackFailure);
        }
    }



    // We return the reserved tickets back to stock by iterating through the items in the order, grouping them by event and zone, and then calling the releaseInventory method on the event for each group of items. For standing zones, we release the inventory by specifying the quantity of tickets being released. For seated zones, we release the inventory by specifying the list of seat numbers being released. After releasing the inventory for all items, we clear the order and save it to persist the changes. This method assumes that we are only trying to return tickets that were reserved but not yet confirmed as sold, and it does not handle any cases where tickets might have already been sold or where there might be other complications in the inventory state.
    private void returnTicketsToStock(ActiveOrder order) {
        List<CartLineItem> returnToStock = order.getItems();

        Map<Integer, Map<Integer, List<CartLineItem>>> grouped = groupItemsByEventAndZone(returnToStock);

        for (Map.Entry<Integer, Map<Integer, List<CartLineItem>>> eventEntry : grouped.entrySet()) {
            Event event = eventRepository.findById(eventEntry.getKey());

            if (event == null) {
                continue;
            }

            for (Map.Entry<Integer, List<CartLineItem>> zoneEntry : eventEntry.getValue().entrySet()) {
                int zoneId = zoneEntry.getKey();
                List<CartLineItem> zoneItems = zoneEntry.getValue();

                List<String> seatNumbers = extractSeatNumbers(zoneItems);

                if (seatNumbers.isEmpty()) {
                    event.releaseInventory(zoneId, InventorySelection.standing(zoneItems.size()));
                } else {
                    event.releaseInventory(zoneId, InventorySelection.seated(seatNumbers));
                }
            }

            eventRepository.save(event);
        }

        order.clear();
        activeOrderRepository.save(order);
    }



    // We attempt to refund the payment during a checkout rollback. This is done as a safety measure in case the checkout process fails after the payment was charged, allowing us to return the funds to the customer. We wrap this in a try-catch block to ensure that if any exceptions occur during the refund (e.g. issues with the payment gateway), we log the error but do not let it propagate further since we are already in an error handling flow and we want to avoid masking the original failure with additional exceptions from the refund process.
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

    

    // We check the idempotency cache for a completed checkout result using the idempotency key and the buyer key. If there is an existing cache entry for the idempotency key, we check if the buyer key matches. If it does not match, we throw an IdempotencyConflictException to indicate that there is a conflict with the idempotency key being used by a different buyer. If it matches, we return the cached checkout result. If there is no existing cache entry for the idempotency key, we return null to indicate that there is no cached result and that we should proceed with processing the checkout as normal.
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


    // We cache the checkout result in the idempotency cache by associating the idempotency key with a new cache entry that contains the buyer key and the checkout result. This allows us to return the same checkout result for subsequent requests that use the same idempotency key and buyer key, while also ensuring that if there is a conflict with the idempotency key being used by a different buyer, we can detect it and throw an appropriate exception. By using putIfAbsent, we ensure that we do not overwrite an existing cache entry if one already exists for the same idempotency key, which helps maintain the integrity of our idempotency handling.
    private void cacheCheckoutResult(String idempotencyKey, String buyerKey, CheckoutResultDTO result) {
        completedCheckoutsByIdempotencyKey.putIfAbsent(
                idempotencyKey,
                new IdempotencyCacheEntry(buyerKey, result));
    }



    
    // We generate a unique key for a member buyer based on their user ID. This key is used to identify the buyer in the idempotency cache and other internal mechanisms.
    private String memberBuyerKey(int userId) {
        return "member:" + userId;
    }

    // We generate a unique key for a guest buyer based on their session ID and email. This key is used to identify the buyer in the idempotency cache and other internal mechanisms. By combining the session ID and email, we can create a more unique identifier for the guest buyer, which helps prevent conflicts in the idempotency cache when multiple guests might be using the same session or when a guest might have multiple sessions.
    private String guestBuyerKey(String guestSessionId, String guestEmail) {
        return "guest:" + guestSessionId + ":" + guestEmail.trim().toLowerCase();
    }

    // We generate a unique lock key for a member buyer based on their user ID. This key is used to acquire a lock for the member's order during the checkout process to prevent concurrent modifications and ensure that only one checkout can be processed for the member at a time.
    private String memberOrderLockKey(int userId) {
        return "user:" + userId;
    }

    // We generate a unique lock key for a guest buyer based on their session ID. This key is used to acquire a lock for the guest's order during the checkout process to prevent concurrent modifications and ensure that only one checkout can be processed for the guest at a time. By using the session ID, we can allow guests to have multiple sessions (e.g. on different devices) while still ensuring that each session is locked separately during checkout.
    private String guestOrderLockKey(String guestSessionId) {
        return "sess:" + guestSessionId;
    }





    // helper record classes for internal use within the service - not part of the public API



    // This is a simple struct to hold a cart line item along with its calculated final price for the checkout. This allows us to calculate prices once and keep the logic clean, especially when we need to build receipt lines later.
    private record PricedCartLine(
            CartLineItem item,
            double finalPrice) {
    }

    
    // This is the value stored in the idempotency cache. It includes the buyerKey to detect conflicts (same idempotency key used by different buyers) and the actual checkout result to return for repeated requests.
    private record IdempotencyCacheEntry(
            String buyerKey,
            CheckoutResultDTO result) {
    }

    

}

