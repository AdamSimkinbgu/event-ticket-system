package com.ticketing.system.Core.Application.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import com.ticketing.system.Core.Domain.events.InventorySelection;
import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;
import com.ticketing.system.Core.Domain.ActiveOrder.CartLineItem;
import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
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
    private final ISessionManager iSessionManager;

    public CheckoutService(
            IActiveOrderRepository activeOrderRepository,
            IEventRepository eventRepository,
            ITicketRepository ticketRepository,
            IOrderReceiptRepository orderReceiptRepository,
            ITicketIssuer ticketIssuer,
            IPaymentGateway paymentGateway,
            INotificationService notificationService,
             ISessionManager iSessionManager
            
    ) {
        this.activeOrderRepository = activeOrderRepository;
        this.eventRepository = eventRepository;
        this.ticketRepository = ticketRepository;
        this.orderReceiptRepository = orderReceiptRepository;
        this.ticketIssuer = ticketIssuer;
        this.paymentGateway = paymentGateway;
        this.notificationService = notificationService;
        this.iSessionManager = iSessionManager;
    }

    





    
    public CheckoutResultDTO checkoutMember(String token, String idempotencyKey, String currency, String paymentMethodToken) {
        log.info("Entered checkout function");

        int userId = -1;
        ActiveOrder order = null;
        PaymentResultDTO paymentResult = null;
        double totalPrice = 0;
        boolean inventorySaleConfirmationStarted = false;

        try {
            userId = authenticateAndGetUserId(token);

            order = activeOrderRepository.getByUserId(userId);

            validatePaymentInput(idempotencyKey, currency, paymentMethodToken, userId);
            validateOrderForCheckout(order, userId);

            List<CartLineItem> boughtItems = order.getItems();

            log.info("Checkout order validated. userId={}, itemCount={}", userId, boughtItems.size());

            totalPrice = calculateTotalPrice(order);

            log.info("Checkout total price calculated. userId={}, totalPrice={}", userId, totalPrice);

            paymentResult = chargePayment(
                    userId,
                    null,
                    totalPrice,
                    idempotencyKey,
                    currency,
                    paymentMethodToken);

            IssuanceResultDTO issuanceResult = issueTickets(userId, null, boughtItems);

            validateIssuanceResult(issuanceResult, boughtItems, userId);

            int orderReceiptId = orderReceiptRepository.nextId();

            List<ReceiptLine> receiptLines = saveTicketsAndBuildReceiptLines(
                    userId,
                    orderReceiptId,
                    boughtItems,
                    issuanceResult);

            inventorySaleConfirmationStarted = true;
            confirmInventorySale(boughtItems);

            saveMemberReceipt(
                    userId,
                    orderReceiptId,
                    totalPrice,
                    receiptLines,
                    paymentResult,
                    issuanceResult);

            order.buy();
            activeOrderRepository.save(order);

            log.info("Order marked as bought. userId={}", userId);

            notifyPurchaseCompleted(userId, totalPrice, receiptLines);

            log.info(
                    "Checkout completed successfully. userId={}, transactionId={}, issuedTicketCount={}",
                    userId,
                    paymentResult.paymentTransactionId(),
                    issuanceResult.barcodes().size());

            return buildCheckoutResult(totalPrice, orderReceiptId, paymentResult, issuanceResult);

        } catch (Exception e) {
            handleCheckoutFailure(userId, order, paymentResult, totalPrice, inventorySaleConfirmationStarted, e);
            throw new RuntimeException("Checkout failed", e);
        }
    }







    
    






    public CheckoutResultDTO checkoutGuest(String guestSessionId, String guestEmail, String idempotencyKey, String currency, String paymentMethodToken) {
        log.info("Entered guest checkout function. guestSessionId={}", guestSessionId);

        ActiveOrder order = null;
        PaymentResultDTO paymentResult = null;
        double totalPrice = 0;
        boolean inventorySaleConfirmationStarted = false;

        try {
            validateGuestCheckoutIdentity(guestSessionId, guestEmail);

            order = activeOrderRepository.getBySessionId(guestSessionId)
                    .orElseThrow(() -> new IllegalStateException("Active guest order not found"));

            validatePaymentInput(idempotencyKey, currency, paymentMethodToken, null);
            validateOrderForCheckout(order, null);

            List<CartLineItem> boughtItems = order.getItems();

            totalPrice = calculateTotalPrice(order);

            paymentResult = chargePayment(
                    null,
                    guestEmail,
                    totalPrice,
                    idempotencyKey,
                    currency,
                    paymentMethodToken);

            IssuanceResultDTO issuanceResult = issueTickets(
                    null,
                    guestEmail,
                    boughtItems);

            validateIssuanceResult(issuanceResult, boughtItems, null);

            int orderReceiptId = orderReceiptRepository.nextId();

            List<ReceiptLine> receiptLines = saveTicketsAndBuildReceiptLines(
                    null,
                    orderReceiptId,
                    boughtItems,
                    issuanceResult);

            inventorySaleConfirmationStarted = true;
            confirmInventorySale(boughtItems);

            saveGuestReceipt(
                    guestEmail,
                    guestSessionId,
                    orderReceiptId,
                    totalPrice,
                    receiptLines,
                    paymentResult,
                    issuanceResult);

            order.buy();
            activeOrderRepository.save(order);

            return buildCheckoutResult(totalPrice, orderReceiptId, paymentResult, issuanceResult);

        } catch (Exception e) {
            handleGuestCheckoutFailure(guestSessionId, order, paymentResult, totalPrice, inventorySaleConfirmationStarted, e);
            throw new RuntimeException("Guest checkout failed", e);
        }
    }












    // to confirm sales we need to call the confirmSale method on the event's venue map for each zone, passing the quantity for standing zones and the seat numbers for seated zones, 
    // so we need to group the bought items by event and zone to know how many tickets to confirm for each zone, and which seat numbers for seated zones.
    private void confirmInventorySale(List<CartLineItem> boughtItems) {
        // we need to group the bought items by event and zone to know how many tickets to confirm for each zone, and which seat numbers for seated zones, so we can call the appropriate confirmSale method on the event's venue map.
        Map<Integer, Map<Integer, List<CartLineItem>>> grouped = boughtItems.stream()
                .collect(Collectors.groupingBy(
                        CartLineItem::geteventId,
                        Collectors.groupingBy(CartLineItem::getzoneId)));

        // then we iterate over the grouped items and call confirmSale on the event's venue map for each zone, passing the quantity for standing zones and the seat numbers for seated zones.
        for (Map.Entry<Integer, Map<Integer, List<CartLineItem>>> eventEntry : grouped.entrySet()) {
            Event event = eventRepository.findById(eventEntry.getKey());
            // we can assume that all items in the same zone for the same event are either standing or seated, because the reservation process should not allow mixing them in the same order.
            for (Map.Entry<Integer, List<CartLineItem>> zoneEntry : eventEntry.getValue().entrySet()) {
                int zoneId = zoneEntry.getKey();
                List<CartLineItem> zoneItems = zoneEntry.getValue();

                List<String> seatNumbers = zoneItems.stream()
                        .map(CartLineItem::getSeatNumber)
                        .filter(s -> s != null)
                        .toList();

                if (seatNumbers.isEmpty()) {
                    event.confirmInventorySale(zoneId, InventorySelection.standing(zoneItems.size()));
                } else {
                    event.confirmInventorySale(zoneId, InventorySelection.seated(seatNumbers));
                }
            }
            // after confirming the sale for all zones of the event, we save the event to persist the changes to the inventory.
            eventRepository.save(event);
        }
    }
    



    //TODO: check this function if it's good   <<<===============================     <<=========================================    <<============================
    // function to move inventory back to available stock in case of checkout failure after payment, we call releaseInventory on the event's venue map for each zone, passing the quantity for standing zones and the seat numbers for seated zones, to return the tickets to stock.
    // so moves from sold -> reserved -> available instead of directly from sold -> available, to keep the inventory state consistent and allow for better tracking and potential analytics on reservation vs actual sales.
    private void reverseConfirmInventorySale(List<CartLineItem> boughtItems) {
        // this is the reverse of confirmInventorySale, we call releaseInventory on the event's venue map for each zone, passing the quantity for standing zones and the seat numbers for seated zones, to return the tickets to stock in case of checkout failure after payment.
        // we need to group the bought items by event and zone to know how many tickets to release for each zone, and which seat numbers for seated zones, so we can call the appropriate releaseInventory method on the event's venue map.
        Map<Integer, Map<Integer, List<CartLineItem>>> grouped = boughtItems.stream()
                .collect(Collectors.groupingBy(
                        CartLineItem::geteventId,
                        Collectors.groupingBy(CartLineItem::getzoneId)));
        // then we iterate over the grouped items and call releaseInventory on the event's venue map for each zone, passing the quantity for standing zones and the seat numbers for seated zones, to return the tickets to stock in case of checkout failure after payment.
        for (Map.Entry<Integer, Map<Integer, List<CartLineItem>>> eventEntry : grouped.entrySet()) {
            Event event = eventRepository.findById(eventEntry.getKey());
            // we can assume that all items in the same zone for the same event are either standing or seated, because the reservation process should not allow mixing them in the same order.
            for (Map.Entry<Integer, List<CartLineItem>> zoneEntry : eventEntry.getValue().entrySet()) {
                int zoneId = zoneEntry.getKey();
                List<CartLineItem> zoneItems = zoneEntry.getValue();
                // we extract the seat numbers for seated zones, if there are any, to pass to the releaseInventory method on the event's venue map, if there are no seat numbers it means it's a standing zone and we just pass the quantity.
                List<String> seatNumbers = zoneItems.stream()
                        .map(CartLineItem::getSeatNumber)
                        .filter(s -> s != null)
                        .toList();

                if (seatNumbers.isEmpty()) {
                    event.releaseInventory(zoneId, InventorySelection.standing(zoneItems.size()));
                } else {
                    event.releaseInventory(zoneId, InventorySelection.seated(seatNumbers));
                }
            }
            eventRepository.save(event);
        }
    }







    private List<TransactionRecord> buildPurchaseTransactions(PaymentResultDTO paymentResult, IssuanceResultDTO issuanceResult) {
        List<TransactionRecord> transactions = new ArrayList<>();

        if (paymentResult != null) {
            transactions.add(TransactionRecord.paymentCharge(
                    paymentResult.paymentTransactionId(),
                    paymentResult.gatewayName(),
                    paymentResult.chargedAmount(),
                    paymentResult.currency(),
                    paymentResult.chargedAt()));
        }

        if (issuanceResult != null) {
            transactions.add(TransactionRecord.ticketIssuance(
                    issuanceResult.issuanceTransactionId(),
                    issuanceResult.issuerName(),
                    issuanceResult.issuedAt()));
        }

        return transactions;
    }


    private void validateGuestCheckoutIdentity(String guestSessionId, String guestEmail) {
        if (guestSessionId == null || guestSessionId.isBlank()) {
            throw new IllegalArgumentException("guestSessionId is required");
        }

        if (!iSessionManager.validateCredential(guestSessionId)) {
            throw new IllegalStateException("Invalid or expired guest session");
        }

        if (guestEmail == null || guestEmail.isBlank()) {
            throw new IllegalArgumentException("guestEmail is required");
        }
    }








    private void handleGuestCheckoutFailure(
        String guestSessionId,
        ActiveOrder order,
        PaymentResultDTO paymentResult,
        double totalPrice,
        boolean inventorySaleConfirmationStarted,
        Exception e
    ) {
        log.error(
                "Guest checkout failed. guestSessionId={}, totalPrice={}, paymentDone={}, inventorySaleConfirmationStarted={}",
                guestSessionId,
                totalPrice,
                paymentResult != null,
                inventorySaleConfirmationStarted,
                e);

        if (!inventorySaleConfirmationStarted) {
            returnTicketsToStock(order);
            log.info("Guest checkout rollback: reserved tickets returned to stock. guestSessionId={}", guestSessionId);
        } else {
            log.error(
                    "Guest checkout failed after inventory sale confirmation started. " +
                            "Inventory may already be SOLD, so it is NOT safe to call releaseInventory. guestSessionId={}",
                    guestSessionId);
        }

        if (paymentResult != null) {
            log.info(
                    "Guest refund requested. guestSessionId={}, transactionId={}, amount={}",
                    guestSessionId,
                    paymentResult.paymentTransactionId(),
                    totalPrice);

            paymentGateway.refund(paymentResult.paymentTransactionId(), totalPrice);
        }
    }






    private double calculateTotalPrice(ActiveOrder order) {
        double totalPrice = 0;

        Map<Integer, List<CartLineItem>> itemsByEvent =
                order.getItems()
                        .stream()
                        .collect(Collectors.groupingBy(CartLineItem::geteventId));

        for (Map.Entry<Integer, List<CartLineItem>> entry : itemsByEvent.entrySet()) {
            int eventId = entry.getKey();
            List<CartLineItem> eventItems = entry.getValue();

            Event event = eventRepository.findById(eventId);
            int eventQuantity = eventItems.size();

            for (CartLineItem item : eventItems) {
                totalPrice += event.calculatePriceforoneticket(
                        eventQuantity,
                        item.getPriceAtReservation(),
                        LocalDateTime.now()
                );
            }
        }

        return totalPrice;
    }

    



    private void returnTicketsToStock(ActiveOrder order) {
        if (order == null) {
            return;
        }

        List<CartLineItem> returnToStock = order.getItems();

        Map<Integer, Map<Integer, List<CartLineItem>>> grouped = returnToStock.stream()
                .collect(Collectors.groupingBy(
                        CartLineItem::geteventId,
                        Collectors.groupingBy(CartLineItem::getzoneId)));

        for (Map.Entry<Integer, Map<Integer, List<CartLineItem>>> eventEntry : grouped.entrySet()) {
            Event event = eventRepository.findById(eventEntry.getKey());

            if (event == null) {
                continue;
            }

            for (Map.Entry<Integer, List<CartLineItem>> zoneEntry : eventEntry.getValue().entrySet()) {
                int zoneId = zoneEntry.getKey();
                List<CartLineItem> zoneItems = zoneEntry.getValue();

                List<String> seatNumbers = zoneItems.stream()
                        .map(CartLineItem::getSeatNumber)
                        .filter(seatNumber -> seatNumber != null)
                        .toList();

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




  private int authenticateAndGetUserId(String token) {
    if (token == null || token.isBlank()) {
        log.warn("Checkout rejected: missing authentication token");
        throw new IllegalArgumentException("Missing authentication token");
    }

    if (!iSessionManager.validateToken(token)) {
        log.warn("Checkout rejected: invalid or expired token");
        throw new IllegalStateException("Invalid or expired authentication token");
    }

    return iSessionManager.extractUserId(token);
}

    private void validatePaymentInput(
            String idempotencyKey,
            String currency,
            String paymentMethodToken,
            Integer userId
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.warn("Checkout rejected: missing idempotency key. userId={}", userId);
            throw new IllegalArgumentException("Missing idempotency key");
        }

        if (currency == null || currency.isBlank()) {
            log.warn("Checkout rejected: missing currency. userId={}", userId);
            throw new IllegalArgumentException("Missing currency");
        }

        if (paymentMethodToken == null || paymentMethodToken.isBlank()) {
            log.warn("Checkout rejected: missing payment method token. userId={}", userId);
            throw new IllegalArgumentException("Missing payment method token");
        }
    }

    private void validateOrderForCheckout(ActiveOrder order, Integer userId) {
        if (order == null) {
            log.warn("Checkout rejected: active order not found. userId={}", userId);
            throw new IllegalStateException("Active order not found");
        }

        if (!order.validateCanCheckout()) {
            log.warn("Checkout rejected: order cannot checkout. userId={}", userId);
            throw new IllegalStateException("Order cannot checkout");
        }
    }

private PaymentResultDTO chargePayment(
        Integer buyerUserId,
        String buyerEmail,
        double totalPrice,
        String idempotencyKey,
        String currency,
        String paymentMethodToken
) {
    log.info(
            "Payment charge requested. buyerUserId={}, buyerEmail={}, totalPrice={}, currency={}",
            buyerUserId,
            buyerEmail,
            totalPrice,
            currency
    );

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

    private IssuanceResultDTO issueTickets(
            Integer buyerUserId,
            String buyerEmail,
            List<CartLineItem> boughtItems
    ) {
        List<IssuanceRequestDTO.TicketIssuanceItemDTO> issuanceItems =
                boughtItems.stream()
                        .map(item -> {
                            Event event = eventRepository.findById(item.geteventId());

                            return new IssuanceRequestDTO.TicketIssuanceItemDTO(
                                    item.geteventId(),
                                    event.getName(),
                                    item.getzoneId(),
                                    item.getSeatNumber()
                            );
                        })
                        .toList();

        log.info(
                "Ticket issuance request built. buyerUserId={}, buyerEmail={}, ticketCount={}",
                buyerUserId,
                buyerEmail,
                issuanceItems.size()
        );

        IssuanceRequestDTO issuanceRequest = new IssuanceRequestDTO(
                buyerUserId,
                buyerEmail,
                issuanceItems
        );

        return ticketIssuer.issue(issuanceRequest);
    }

    private void validateIssuanceResult(IssuanceResultDTO issuanceResult, List<CartLineItem> boughtItems, Integer userId) {
        if (issuanceResult == null || issuanceResult.barcodes() == null || issuanceResult.barcodes().isEmpty()) {
            log.error("Ticket issuance failed. userId={}, itemCount={}", userId, boughtItems.size());
            throw new IllegalStateException("Ticket issuance failed");
        }

        if (issuanceResult.barcodes().size() != boughtItems.size()) {
            log.error(
                    "Ticket issuance count mismatch. userId={}, expected={}, actual={}",
                    userId,
                    boughtItems.size(),
                    issuanceResult.barcodes().size());
            throw new IllegalStateException("Ticket issuance count mismatch");
        }

        log.info(
                "Ticket issuance succeeded. userId={}, issuedCount={}",
                userId,
                issuanceResult.barcodes().size());
    }

    
    //*Note : Future guest checkout can call this method with null holderUserId 
    // For guests, the ticket is still linked to the receipt through orderReceiptId, but it does not get a fake member user id.
    // */
    private List<ReceiptLine> saveTicketsAndBuildReceiptLines(Integer holderUserId, int orderReceiptId, List<CartLineItem> boughtItems, IssuanceResultDTO issuanceResult) {
        List<ReceiptLine> receiptLines = new ArrayList<>();

        for (int i = 0; i < boughtItems.size(); i++) {
            CartLineItem item = boughtItems.get(i);
            var barcode = issuanceResult.barcodes().get(i);

            Event event = eventRepository.findById(item.geteventId());

            long quantityForSameEvent = boughtItems.stream()
                    .filter(x -> x.geteventId() == item.geteventId())
                    .count();

            double finalPriceForOneTicket = event.calculatePriceforoneticket(
                    (int) quantityForSameEvent,
                    item.getPriceAtReservation(),
                    LocalDateTime.now());

            Ticket ticket = new Ticket(
                    item.geteventId(),
                    item.getzoneId(),
                    orderReceiptId,
                    item.getSeatNumber(),
                    finalPriceForOneTicket,
                    barcode.ticketId(),
                    barcode.barcodeValue());

            if (holderUserId != null) {
                ticket.setHolderUserId(holderUserId);
            }

            ticketRepository.save(ticket);

            ReceiptLine line = new ReceiptLine(
                    barcode.ticketId(),
                    finalPriceForOneTicket,
                    item.geteventId(),
                    item.getzoneId(),
                    item.getSeatNumber(),
                    LocalDateTime.now());

            receiptLines.add(line);
        }

        return receiptLines;
    }









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
                buildPurchaseTransactions(paymentResult, issuanceResult));

        orderReceiptRepository.save(receipt);

        log.info(
                "Member order receipt saved. receiptId={}, userId={}, totalPrice={}, receiptLineCount={}",
                receiptId,
                userId,
                totalPrice,
                receiptLines.size());
    }

    
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

        log.info(
                "Guest order receipt saved. receiptId={}, guestEmail={}, guestSessionId={}, totalPrice={}, receiptLineCount={}",
                receiptId,
                guestEmail,
                guestSessionId,
                totalPrice,
                receiptLines.size());
    }
    












    private void notifyPurchaseCompleted(int userId, double totalPrice, List<ReceiptLine> receiptLines) {
        notificationService.notifyPurchaseCompleted(
                userId,
                totalPrice,
                receiptLines.stream()
                        .map(ReceiptLine::getTicketId)
                        .toList()
        );

        log.info("Purchase completed notification sent. userId={}", userId);
    }

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

    

    private void handleCheckoutFailure(
        int userId,
        ActiveOrder order,
        PaymentResultDTO paymentResult,
        double totalPrice,
        boolean inventorySaleConfirmationStarted,
        Exception e
    ) {
        log.error(
                "Checkout failed. userId={}, totalPrice={}, paymentDone={}, inventorySaleConfirmationStarted={}",
                userId,
                totalPrice,
                paymentResult != null,
                inventorySaleConfirmationStarted,
                e);

        if (!inventorySaleConfirmationStarted) {
            returnTicketsToStock(order);
            log.info("Checkout rollback: reserved tickets returned to stock. userId={}", userId);
        } else {
            log.error(
                    "Checkout failed after inventory sale confirmation started. " +
                            "Inventory may already be SOLD, so it is NOT safe to call releaseInventory. userId={}",
                    userId);
        }

        if (paymentResult != null) {
            if (userId > 0) {
                notificationService.notifyPurchaseFailed(
                        userId,
                        "Checkout failed. A refund was requested.");
            }

            log.info(
                    "Refund requested. userId={}, transactionId={}, amount={}",
                    userId,
                    paymentResult.paymentTransactionId(),
                    totalPrice);

            paymentGateway.refund(paymentResult.paymentTransactionId(), totalPrice);
        }

        if (userId > 0) {
            notificationService.notifyPurchaseFailed(
                    userId,
                    "Checkout failed.");
        }
    }


}