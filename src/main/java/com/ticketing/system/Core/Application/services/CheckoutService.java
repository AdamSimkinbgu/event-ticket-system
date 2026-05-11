package com.ticketing.system.Core.Application.services;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.ticketing.system.Core.Application.dto.IssuanceRequestDTO;
import com.ticketing.system.Core.Application.dto.IssuanceResultDTO;
import com.ticketing.system.Core.Application.interfaces.IPaymentGateway;
import com.ticketing.system.Core.Application.interfaces.ITicketIssuer;
import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;
import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;
import com.ticketing.system.Core.Domain.ActiveOrder.CartLineItem;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.orders.ReceiptLine;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;
import com.ticketing.system.Core.Domain.orders.TransactionRecord;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;

public class CheckoutService {

    private final IActiveOrderRepository activeOrderRepository;
    private final IEventRepository eventRepository;
      private final ITicketRepository ticketRepository;
    private final IOrderReceiptRepository orderReceiptRepository;
    private final ITicketIssuer ticketIssuer;

    public CheckoutService( IActiveOrderRepository activeOrderRepository,IEventRepository eventRepository,ITicketRepository ticketRepository, IOrderReceiptRepository orderReceiptRepository,ITicketIssuer ticketIssuer) {
        this.activeOrderRepository = activeOrderRepository;
        this.eventRepository = eventRepository;
        this.ticketRepository = ticketRepository;
        this.orderReceiptRepository = orderReceiptRepository;
        this.ticketIssuer=ticketIssuer;
    }
public void checkout(String userId, PaymentGateway paymentGateway) {

    ActiveOrder order = activeOrderRepository.getByUserId(userId);

    try {
        if (!order.validateCanCheckout()) {
            throw new IllegalStateException("Order cannot checkout");
        }

        double totalPrice = 0;

        Map<String, List<CartLineItem>> itemsByEvent = order.getItems().stream() .collect(Collectors.groupingBy(CartLineItem::geteventId));

        for (Map.Entry<String, List<CartLineItem>> entry : itemsByEvent.entrySet()) {
            String eventId = entry.getKey();
            List<CartLineItem> eventItems = entry.getValue();

            Event event = eventRepository.findById(eventId);

            Map<Integer, Double> tickets =  java.util.stream.IntStream.range(0, eventItems.size()) .boxed().collect(Collectors.toMap( i -> i, i -> eventItems.get(i).getPriceAtReservation()  ));

            totalPrice += event.calculatePrice(tickets, LocalDateTime.now());
        }

        if (!paymentGateway.Paying(totalPrice)) {
            throw new IllegalStateException("Payment failed");
        }

        List<CartLineItem> boughtItems = order.buy();

        List<IssuanceRequestDTO.TicketIssuanceItemDTO> issuanceItems =
                boughtItems.stream()
                        .map(item -> {
                            Event event = eventRepository.findById(item.geteventId());

                            return new IssuanceRequestDTO.TicketIssuanceItemDTO(
                                    null,
                                    item.geteventId(),
                                    event.getName(),
                                    item.getzoneId(),
                                    null
                            );
                        })
                        .toList();

        IssuanceRequestDTO request = new IssuanceRequestDTO(
                null,
                Integer.parseInt(userId),
                null,
                issuanceItems
        );

        IssuanceResultDTO result = ticketIssuer.issue(request);

       if (result == null || result.barcodes() == null || result.barcodes().isEmpty()) {
    throw new IllegalStateException("Ticket issuance failed");
}
        for (CartLineItem item : boughtItems) {
            Ticket ticket = new Ticket(
                    item.geteventId(),
                    item.getzoneId(),
                    item.getPriceAtReservation()
            );

            OrderReceipt receipt = new OrderReceipt(
                    item.geteventId(),
                    item.getzoneId(),
                    item.getPriceAtReservation()
            );

            ticketRepository.save(ticket);
            orderReceiptRepository.save(receipt);
        }

    } catch (Exception e) {
        List<CartLineItem> returnToStock = order.ReturnToStock();

        for (CartLineItem item : returnToStock) {
            Event event = eventRepository.findById(item.geteventId());

            event.releaseTickets(item.getzoneId(), 1);

            eventRepository.save(event);
        }

        throw new IllegalStateException("Checkout failed, tickets returned to stock", e);
    }
}




//     public void checkout(String userId, PaymentGateway PaymentGateway) {
//         ActiveOrder order = activeOrderRepository.getByUserId(userId);

//             try {
//              List <CartLineItem>  temp =order.getItems();


//             double price = order.calculateTotalPrice(); // יש בעיה

//             if (order.validateCanCheckout()&& PaymentGateway.Paying(price)  ) {
                  
//             for (CartLineItem item : order.buy()) {
//                 Ticket ticket = new Ticket( item.geteventId(), item.getzoneId(), item.getPriceAtReservation() );
//                  OrderReceipt neworder = new OrderReceipt(item.geteventId(), item.getzoneId(), item.getPriceAtReservation() );
//                 ticketRepository.save(ticket);
//                 orderReceiptRepository.save(neworder);
//             }
//                throw new IllegalStateException("Payment failed");
//         }
//     }
//         catch (Exception e) {
//         List<CartLineItem> returnToStock = order.ReturnToStock();

//          }
//    }

}