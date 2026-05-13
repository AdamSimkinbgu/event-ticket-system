package com.ticketing.system.Core.Application.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.system.Core.Application.dto.CheckoutResultDTO;
import com.ticketing.system.Core.Application.dto.IssuanceRequestDTO;
import com.ticketing.system.Core.Application.dto.IssuanceResultDTO;
import com.ticketing.system.Core.Application.dto.PaymentRequestDTO;
import com.ticketing.system.Core.Application.dto.PaymentResultDTO;
import com.ticketing.system.Core.Application.interfaces.INotificationService;
import com.ticketing.system.Core.Application.interfaces.IPaymentGateway;
import com.ticketing.system.Core.Application.interfaces.ITicketIssuer;
import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;
import com.ticketing.system.Core.Domain.ActiveOrder.CartLineItem;
import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;
import com.ticketing.system.Core.Domain.orders.ReceiptLine;

public class CheckoutService {

    private final IActiveOrderRepository activeOrderRepository;
    private final IEventRepository eventRepository;
    private final ITicketRepository ticketRepository;
    private final IOrderReceiptRepository orderReceiptRepository;
    private final ITicketIssuer ticketIssuer;
    private final IPaymentGateway paymentGateway;
    private final INotificationService notificationService;
    private final AuthenticationService authenticationService;
     private static final Logger eventLogger = LoggerFactory.getLogger("EVENT_LOG");

private static final Logger errorLogger = LoggerFactory.getLogger("ERROR_LOG");


    public CheckoutService(
            IActiveOrderRepository activeOrderRepository,
            IEventRepository eventRepository,
            ITicketRepository ticketRepository,
            IOrderReceiptRepository orderReceiptRepository,
            ITicketIssuer ticketIssuer,
            IPaymentGateway paymentGateway,
            INotificationService notificationService,
             AuthenticationService authenticationService
            
    ) {
        this.activeOrderRepository = activeOrderRepository;
        this.eventRepository = eventRepository;
        this.ticketRepository = ticketRepository;
        this.orderReceiptRepository = orderReceiptRepository;
        this.ticketIssuer = ticketIssuer;
        this.paymentGateway = paymentGateway;
        this.notificationService=notificationService;
         this.authenticationService = authenticationService;
    }

    public CheckoutResultDTO checkout(String token, PaymentRequestDTO paymentRequest) {
        eventLogger.info("Entered checkout function");
   if (token == null || token.isBlank()) {
      eventLogger.warn("Checkout rejected: missing authentication token");
    throw new IllegalArgumentException("Missing authentication token");
}

if (!authenticationService.validateToken(token)) {
     eventLogger.warn("Checkout rejected: invalid or expired token");
    throw new IllegalStateException("Invalid or expired authentication token");
}

         int userId = authenticationService.extractUserId(token);
        ActiveOrder order = activeOrderRepository.getByUserId(userId);
         eventLogger.info("Active order fetched. userId={}, orderExists={}", userId, order != null);
        PaymentResultDTO paymentResult = null;
          double totalPrice = 0;

        try {
            if (order == null) {
                  eventLogger.warn("Checkout rejected: active order not found. userId={}", userId);
                throw new IllegalStateException("Active order not found");
            }

            if (!order.validateCanCheckout()) {
         eventLogger.warn("Checkout rejected:There is an expired card, order cannot checkout. userId={}", userId);
                throw new IllegalStateException("Order cannot checkout");
            }
            List<CartLineItem> boughtItems = order.getItems();
             eventLogger.info("Checkout order validated. userId={}, itemCount={}", userId, boughtItems.size());

             totalPrice = calculateTotalPrice(order);
           eventLogger.info("Checkout total price calculated. userId={}, totalPrice={}", userId, totalPrice);

             eventLogger.info("Payment charge requested. userId={}, totalPrice={}, currency={}",
                userId, totalPrice, paymentRequest.currency());
            PaymentRequestDTO requestToPay = new PaymentRequestDTO(
                    paymentRequest.idempotencyKey(),
                    totalPrice,
                    paymentRequest.currency(),
                    paymentRequest.paymentMethodToken(),
                    userId
            );

            


             if (paymentRequest==null) {
    eventLogger.warn("Checkout rejected: missing payment request");
    throw new IllegalArgumentException("Missing payment request");
}

if (paymentRequest.idempotencyKey() == null || paymentRequest.idempotencyKey().isBlank()) {
    eventLogger.warn("Checkout rejected: missing idempotency key. userId={}", userId);
    throw new IllegalArgumentException("Missing idempotency key");
}

if (paymentRequest.currency() == null || paymentRequest.currency().isBlank()) {
    eventLogger.warn("Checkout rejected: missing currency. userId={}", userId);
    throw new IllegalArgumentException("Missing currency");
}

if (paymentRequest.paymentMethodToken() == null || paymentRequest.paymentMethodToken().isBlank()) {
    eventLogger.warn("Checkout rejected: missing payment method token. userId={}", userId);
    throw new IllegalArgumentException("Missing payment method token");
}

          paymentResult = paymentGateway.charge(requestToPay);  
            

            List<IssuanceRequestDTO.TicketIssuanceItemDTO> issuanceItems =
                    boughtItems.stream()
                            .map(item -> {
                                Event event = eventRepository.findById(item.geteventId());

                                return new IssuanceRequestDTO.TicketIssuanceItemDTO(
                                    item.geteventId(),
                                        event.getName(),
                                        item.getzoneId(),
                                        null
                                );
                            })
                            .toList();
                             eventLogger.info("Ticket issuance request built. userId={}, ticketCount={}",
                userId, issuanceItems.size());

            IssuanceRequestDTO issuanceRequest = new IssuanceRequestDTO(
                       userId,
                    null,
                    issuanceItems
            );

            IssuanceResultDTO issuanceResult = ticketIssuer.issue(issuanceRequest);

            if (issuanceResult == null || issuanceResult.barcodes() == null || issuanceResult.barcodes().isEmpty()) {
                  errorLogger.error("Ticket issuance failed. userId={}, itemCount={}", userId, boughtItems.size());
                throw new IllegalStateException("Ticket issuance failed");
            }  
            if (issuanceResult.barcodes().size() != boughtItems.size()) {
                  errorLogger.error("Ticket issuance count mismatch. userId={}, expected={}, actual={}",
                    userId, boughtItems.size(), issuanceResult.barcodes().size());
    throw new IllegalStateException("Ticket issuance count mismatch");
}

eventLogger.info("Ticket issuance succeeded. userId={}, issuedCount={}",
                userId, issuanceResult.barcodes().size());
            order.buy();
             eventLogger.info("Order marked as bought. userId={}", userId);
            
            List<ReceiptLine> receiptLines = new ArrayList<>();

for (int i = 0; i < boughtItems.size(); i++) {
    CartLineItem item = boughtItems.get(i);
    var barcode = issuanceResult.barcodes().get(i);

    Ticket ticket = new Ticket(
            item.geteventId(),
            item.getzoneId(),
            item.getPriceAtReservation(),
            barcode.ticketId(),
            barcode.barcodeValue()
    );

    ticketRepository.save(ticket);
 eventLogger.info("Ticket saved. userId={}, ticketId={}, eventId={}, zoneId={}",
                    userId,
                    barcode.ticketId(),
                    item.geteventId(),
                    item.getzoneId());
        
    ReceiptLine line = new ReceiptLine(
            barcode.ticketId(),
            item.getPriceAtReservation(),
            item.geteventId(),
            LocalDateTime.now()
    );

    receiptLines.add(line);
      

}

   OrderReceipt receipt = new OrderReceipt(userId, totalPrice, receiptLines);

      orderReceiptRepository.save(receipt);
      eventLogger.info("Order receipt saved. userId={}, totalPrice={}, receiptLineCount={}",
                userId, totalPrice, receiptLines.size());

      notificationService.notifyPurchaseCompleted(
        userId,
        totalPrice,
        receiptLines.stream()
                .map(ReceiptLine::getTicketId)
                .toList()
);
   eventLogger.info("Purchase completed notification sent. userId={}", userId);
     eventLogger.info("Checkout completed successfully. userId={}, transactionId={}, issuedTicketCount={}",
                userId,
                paymentResult.paymentTransactionId(),
                issuanceResult.barcodes().size());

            return new CheckoutResultDTO(
                    totalPrice,
                    paymentResult.paymentTransactionId(),
                    issuanceResult.barcodes()
                            .stream()
                            .map(barcode -> barcode.ticketId())
                            .toList()
            );

            }   catch (Exception e) {
                   errorLogger.error("Checkout failed. userId={}, totalPrice={}, paymentDone={}",
                userId,
                totalPrice,
                paymentResult != null,
                e);
    returnTicketsToStock(order);
     eventLogger.info("Checkout rollback: tickets returned to stock. userId={}", userId);

    if (paymentResult != null ) {
        
        notificationService.notifyPurchaseFailed( userId,"Checkout failed, we want give you back the money" );  
            eventLogger.info("Refund requested. userId={}, transactionId={}, amount={}",
                    userId,
                    paymentResult.paymentTransactionId(),
                    totalPrice);

         paymentGateway.refund( paymentResult.paymentTransactionId(),totalPrice );
    }
     notificationService.notifyPurchaseFailed( userId,"Checkout failed. Tickets were returned to stock." );  
         eventLogger.info("Refund requested. userId={}, transactionId={}, amount={}",
                    userId,
                    paymentResult.paymentTransactionId(),
                    totalPrice);

    throw new RuntimeException("Checkout failed, tickets returned to stock", e);
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

            Map<Integer, Double> tickets =
                    java.util.stream.IntStream
                            .range(0, eventItems.size())
                            .boxed()
                            .collect(Collectors.toMap(
                                    i -> i,
                                    i -> eventItems.get(i).getPriceAtReservation()
                            ));

            totalPrice += event.calculatePrice(tickets, LocalDateTime.now());
        }

        return totalPrice;
    }

    private void returnTicketsToStock(ActiveOrder order) {
        if (order == null) {
            return;
        }

        List<CartLineItem> returnToStock = order.ReturnToStock();

        for (CartLineItem item : returnToStock) {
            Event event = eventRepository.findById(item.geteventId());

            event.releaseTickets(item.getzoneId(), 1);

            eventRepository.save(event);
        }
    }
}
