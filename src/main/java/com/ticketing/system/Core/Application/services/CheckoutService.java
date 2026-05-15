package com.ticketing.system.Core.Application.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Clock;
import java.util.Optional;
import java.util.regex.Pattern;

import com.ticketing.system.Core.Application.dto.CheckoutResultDTO;
import com.ticketing.system.Core.Application.dto.GuestCheckoutContactDTO;
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
import com.ticketing.system.Core.Domain.exceptions.GuestCheckoutMissingContactException;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Core.Domain.exceptions.SessionExpiredException;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;
import com.ticketing.system.Core.Domain.orders.ReceiptLine;
import com.ticketing.system.Core.Domain.users.ISessionRepository;
import com.ticketing.system.Core.Domain.users.Session;

public class CheckoutService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final IActiveOrderRepository activeOrderRepository;
    private final IEventRepository eventRepository;
    private final ITicketRepository ticketRepository;
    private final IOrderReceiptRepository orderReceiptRepository;
    private final ITicketIssuer ticketIssuer;
    private final IPaymentGateway paymentGateway;
    private final INotificationService notificationService;
    private final ISessionManager iSessionManager;
    private final ISessionRepository sessionRepository;
    private final Clock clock;
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
             ISessionManager iSessionManager,
             ISessionRepository sessionRepository,
             Clock clock
    ) {
        this.activeOrderRepository = activeOrderRepository;
        this.eventRepository = eventRepository;
        this.ticketRepository = ticketRepository;
        this.orderReceiptRepository = orderReceiptRepository;
        this.ticketIssuer = ticketIssuer;
        this.paymentGateway = paymentGateway;
        this.notificationService=notificationService;
         this.iSessionManager =iSessionManager;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    public CheckoutResultDTO checkout(String token, PaymentRequestDTO paymentRequest) {
        eventLogger.info("Entered checkout function");
   if (token == null || token.isBlank()) {
      eventLogger.warn("Checkout rejected: missing authentication token");
    throw new IllegalArgumentException("Missing authentication token");
}

if (!iSessionManager.validateToken(token)) {
     eventLogger.warn("Checkout rejected: invalid or expired token");
    throw new IllegalStateException("Invalid or expired authentication token");
}

         int userId = iSessionManager.extractUserId(token);
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
                    Integer.valueOf(userId),
                    paymentRequest.buyerEmail()
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

    // ---------------------------------------------------------------------
    // Guest checkout (D5 reversed — Guests can complete a purchase).
    // ---------------------------------------------------------------------

    /**
     * Guest variant of {@link #checkout(String, PaymentRequestDTO)}. The
     * Guest provides their session id (raw, not a JWT) plus email + name
     * via {@link GuestCheckoutContactDTO}; the receipt and issued ticket
     * barcodes are keyed by email + sessionId, not by userId.
     *
     * <p>SQ3 deferred: Guest purchase notification semantics are unclear
     * (no in-system Guest inbox), so this method does not call
     * {@link INotificationService}. Revisit once requirement is clarified.
     */
    public CheckoutResultDTO checkoutAsGuest(
            String sessionId,
            GuestCheckoutContactDTO contact,
            PaymentRequestDTO paymentRequest) {

        eventLogger.info("Entered checkoutAsGuest function");

        // 1. Input validation (cheap, before any IO).
        if (sessionId == null || sessionId.isBlank()) {
            eventLogger.warn("Guest checkout rejected: missing sessionId");
            throw new IllegalArgumentException("Missing guest sessionId");
        }
        requireValidGuestContact(contact);
        requireValidPaymentRequest(paymentRequest);

        // 2. Session must exist and still be a Guest.
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new InvalidTokenException("guest session not found"));
        if (session.isMember()) {
            throw new InvalidTokenException("session is not a guest session");
        }
        if (session.isExpiredAt(clock.instant())) {
            throw new SessionExpiredException();
        }

        // 3. Cart lookup.
        ActiveOrder order = activeOrderRepository.getBySessionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Active order not found"));

        order.validateCanCheckout();  // throws if empty / expired

        // 4. Total.
        java.util.List<CartLineItem> boughtItems = order.getItems();
        double totalPrice = calculateTotalPrice(order);

        eventLogger.info("Guest checkout. sid={} email={} totalPrice={} itemCount={}",
                sessionId, contact.email(), totalPrice, boughtItems.size());

        // 5. Charge — email replaces userId in the gateway request.
        PaymentRequestDTO requestToPay = new PaymentRequestDTO(
                paymentRequest.idempotencyKey(),
                totalPrice,
                paymentRequest.currency(),
                paymentRequest.paymentMethodToken(),
                null,                          // buyerUserId — null for Guest
                contact.email()
        );
        PaymentResultDTO paymentResult = paymentGateway.charge(requestToPay);

        // 6. Issue tickets.
        java.util.List<IssuanceRequestDTO.TicketIssuanceItemDTO> issuanceItems =
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

        IssuanceRequestDTO issuanceRequest = new IssuanceRequestDTO(null, contact.email(), issuanceItems);
        IssuanceResultDTO issuanceResult = ticketIssuer.issue(issuanceRequest);

        if (issuanceResult == null || issuanceResult.barcodes() == null
                || issuanceResult.barcodes().isEmpty()) {
            errorLogger.error("Guest ticket issuance failed. sid={} email={}",
                    sessionId, contact.email());
            throw new IllegalStateException("Ticket issuance failed");
        }
        if (issuanceResult.barcodes().size() != boughtItems.size()) {
            errorLogger.error("Guest ticket issuance count mismatch. sid={}, expected={}, actual={}",
                    sessionId, boughtItems.size(), issuanceResult.barcodes().size());
            throw new IllegalStateException("Ticket issuance count mismatch");
        }

        // 7. Save tickets + build receipt.
        order.buy();
        java.util.List<ReceiptLine> receiptLines = new java.util.ArrayList<>();
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
            ReceiptLine line = new ReceiptLine(
                    barcode.ticketId(),
                    item.getPriceAtReservation(),
                    item.geteventId(),
                    LocalDateTime.now()
            );
            receiptLines.add(line);
        }

        OrderReceipt receipt = OrderReceipt.forGuest(
                contact.email(), sessionId, totalPrice, receiptLines);
        orderReceiptRepository.save(receipt);

        // 8. SQ3: Guest notification deferred — INotificationService is Member-only.
        // TODO(SQ3): clarify Guest purchase notification requirement.

        eventLogger.info("Guest checkout completed. sid={} email={} transactionId={}",
                sessionId, contact.email(), paymentResult.paymentTransactionId());

        return new CheckoutResultDTO(
                totalPrice,
                paymentResult.paymentTransactionId(),
                issuanceResult.barcodes().stream().map(b -> b.ticketId()).toList()
        );
    }

    private void requireValidGuestContact(GuestCheckoutContactDTO contact) {
        if (contact == null) {
            throw new GuestCheckoutMissingContactException("guest checkout requires contact info");
        }
        if (contact.email() == null || !EMAIL_PATTERN.matcher(contact.email()).matches()) {
            throw new GuestCheckoutMissingContactException("guest checkout email is invalid");
        }
        if (contact.name() == null || contact.name().isBlank()) {
            throw new GuestCheckoutMissingContactException("guest checkout name is required");
        }
    }

    private void requireValidPaymentRequest(PaymentRequestDTO paymentRequest) {
        if (paymentRequest == null) {
            throw new IllegalArgumentException("Missing payment request");
        }
        if (paymentRequest.idempotencyKey() == null || paymentRequest.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("Missing idempotency key");
        }
        if (paymentRequest.currency() == null || paymentRequest.currency().isBlank()) {
            throw new IllegalArgumentException("Missing currency");
        }
        if (paymentRequest.paymentMethodToken() == null || paymentRequest.paymentMethodToken().isBlank()) {
            throw new IllegalArgumentException("Missing payment method token");
        }
    }
}
