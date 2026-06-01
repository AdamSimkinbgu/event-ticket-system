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
import com.ticketing.system.Core.Application.dto.InventorySelectionDTO;
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
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;
import com.ticketing.system.Core.Domain.orders.ReceiptLine;

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

    





    //TODO: currently this seems to be only for members, as it requires authentication and saves receipts with userId. 
    // We might want to add a guest checkout flow that doesn't require authentication, saves receipts with guestEmail instead of userId.
    public CheckoutResultDTO checkout(String token, String idempotencyKey, String currency, String paymentMethodToken) {
        log.info("Entered checkout function");
        int userId = -1;
        ActiveOrder order = null;
        PaymentResultDTO paymentResult = null;
        double totalPrice = 0;

        try {

            userId = authenticateAndGetUserId(token);
            order = activeOrderRepository.getByUserId(userId);
            validatePaymentInput(idempotencyKey, currency, paymentMethodToken, userId);
            validateOrderForCheckout(order, userId);

            List<CartLineItem> boughtItems = order.getItems();
            log.info("Checkout order validated. userId={}, itemCount={}", userId, boughtItems.size());

            totalPrice = calculateTotalPrice(order);
            log.info("Checkout total price calculated. userId={}, totalPrice={}", userId, totalPrice);

            paymentResult = chargePayment(userId, totalPrice, idempotencyKey, currency, paymentMethodToken);

            IssuanceResultDTO issuanceResult = issueTickets(userId, boughtItems);
            validateIssuanceResult(issuanceResult, boughtItems, userId);
            
            int orderReceiptId = orderReceiptRepository.nextId();
            List<ReceiptLine> receiptLines = saveTicketsAndBuildReceiptLines(userId, orderReceiptId, boughtItems, issuanceResult);

            confirmInventorySale(boughtItems);

            saveReceipt(userId, orderReceiptId, totalPrice, receiptLines);
            order.buy();
            log.info("Order marked as bought. userId={}", userId);

            notifyPurchaseCompleted(userId, totalPrice, receiptLines);

            log.info(
                    "Checkout completed successfully. userId={}, transactionId={}, issuedTicketCount={}",
                    userId,
                    paymentResult.paymentTransactionId(),
                    issuanceResult.barcodes().size());

            return buildCheckoutResult(totalPrice, paymentResult, issuanceResult);

        } catch (Exception e) {
            handleCheckoutFailure(userId, order, paymentResult, totalPrice, e);
            throw new RuntimeException("Checkout failed, tickets returned to stock", e);
        }
    }




    //TODO: maybe add a checkout for guest users that doesn't require authentication, but requires email and sessionId for receipt purposes, and  saves it with a null userId.
    // The flow would be the same, but the userId would be null and the email would be used for notifications instead of userId. This would allow guest users to checkout without creating an account, but still receive notifications and have a record of their purchase.













    // to confirm sales we need to call the confirmSale method on the event's venue map for each zone, passing the quantity for standing zones and the seat numbers for seated zones, 
    // so we need to group the bought items by event and zone to know how many tickets to confirm for each zone, and which seat numbers for seated zones.
    private void confirmInventorySale(List<CartLineItem> boughtItems) {
        // we need to group the bought items by event and zone to know how many tickets to confirm for each zone, and which seat numbers for seated zones, so we can call the appropriate confirmSale method on the event's venue map.
        Map<Integer, Map<Integer, List<CartLineItem>>> grouped =
                boughtItems.stream()
                        .collect(Collectors.groupingBy(
                                CartLineItem::geteventId,
                                Collectors.groupingBy(CartLineItem::getzoneId)
                        ));
        
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
                    event.confirmInventorySale(zoneId, InventorySelectionDTO.standing(zoneItems.size()));
                } else {
                    event.confirmInventorySale(zoneId, InventorySelectionDTO.seated(seatNumbers));
                }
            }
            // after confirming the sale for all zones of the event, we save the event to persist the changes to the inventory.
            eventRepository.save(event);
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
        
        // now we release inventory first...
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
                        .filter(s -> s != null)
                        .toList();

                if (seatNumbers.isEmpty()) {
                    event.releaseInventory(zoneId, InventorySelectionDTO.standing(zoneItems.size()));
                } else {
                    event.releaseInventory(zoneId, InventorySelectionDTO.seated(seatNumbers));
                }
            }
            eventRepository.save(event);
        }

        // now we clear the order
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
        int userId
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

private void validateOrderForCheckout(ActiveOrder order, int userId) {
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
        int userId,
        double totalPrice,
        String idempotencyKey,
        String currency,
        String paymentMethodToken
) {
    log.info(
            "Payment charge requested. userId={}, totalPrice={}, currency={}",
            userId,
            totalPrice,
            currency
    );

    PaymentRequestDTO requestToPay = new PaymentRequestDTO(
            idempotencyKey,
            totalPrice,
            currency,
            paymentMethodToken,
            userId,
            ""
    );

    return paymentGateway.charge(requestToPay);
}

    private IssuanceResultDTO issueTickets(int userId, List<CartLineItem> boughtItems) {
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
                "Ticket issuance request built. userId={}, ticketCount={}",
                userId,
                issuanceItems.size()
        );

        IssuanceRequestDTO issuanceRequest = new IssuanceRequestDTO(
                userId,
                null,
                issuanceItems
        );

        return ticketIssuer.issue(issuanceRequest);
    }

    private void validateIssuanceResult( IssuanceResultDTO issuanceResult,  List<CartLineItem> boughtItems,  int userId
    ) {
        if (issuanceResult == null || issuanceResult.barcodes() == null || issuanceResult.barcodes().isEmpty()) {
            log.error("Ticket issuance failed. userId={}, itemCount={}", userId, boughtItems.size());
            throw new IllegalStateException("Ticket issuance failed");
        }

        if (issuanceResult.barcodes().size() != boughtItems.size()) {
            log.error(
                    "Ticket issuance count mismatch. userId={}, expected={}, actual={}",
                    userId,
                    boughtItems.size(),
                    issuanceResult.barcodes().size()
            );
            throw new IllegalStateException("Ticket issuance count mismatch");
        }

        log.info(
                "Ticket issuance succeeded. userId={}, issuedCount={}",
                userId,
                issuanceResult.barcodes().size()
        );
    }

    private List<ReceiptLine> saveTicketsAndBuildReceiptLines(int holderId, int orderReceiptId, List<CartLineItem> boughtItems,
            IssuanceResultDTO issuanceResult) {
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

            ticket.setHolderUserId(holderId);

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


    //TODO: currently this is only for the member(userId here)
    private void saveReceipt(int userId, int receiptId, double totalPrice, List<ReceiptLine> receiptLines) {
        OrderReceipt receipt = new OrderReceipt(receiptId, userId, totalPrice, receiptLines);
        orderReceiptRepository.save(receipt);

        log.info(
                "Order receipt saved. receiptId={}, userId={}, totalPrice={}, receiptLineCount={}",
                receiptId,
                userId,
                totalPrice,
                receiptLines.size()
        );
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
            PaymentResultDTO paymentResult,
            IssuanceResultDTO issuanceResult
    ) {
        return new CheckoutResultDTO(
                totalPrice,
                paymentResult.paymentTransactionId(),
                issuanceResult.barcodes()
                        .stream()
                        .map(barcode -> barcode.ticketId())
                        .toList()
        );
    }

    private void handleCheckoutFailure(int userId, ActiveOrder order, PaymentResultDTO paymentResult, double totalPrice, Exception e) {
        log.error(
                "Checkout failed. userId={}, totalPrice={}, paymentDone={}",
                userId,
                totalPrice,
                paymentResult != null,
                e
        );

        returnTicketsToStock(order);
        log.info("Checkout rollback: tickets returned to stock. userId={}", userId);

        if (paymentResult != null) {
            notificationService.notifyPurchaseFailed(
                    userId,
                    "Checkout failed, we want give you back the money"
            );

            log.info(
                    "Refund requested. userId={}, transactionId={}, amount={}",
                    userId,
                    paymentResult.paymentTransactionId(),
                    totalPrice
            );

            paymentGateway.refund(paymentResult.paymentTransactionId(), totalPrice);
        }

        notificationService.notifyPurchaseFailed(
                userId,
                "Checkout failed. Tickets were returned to stock."
        );
    }
}