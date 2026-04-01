package com.bgu.se.ticketing.application.services;

import com.bgu.se.ticketing.application.dto.CreateOrderRequestDTO;
import com.bgu.se.ticketing.application.dto.OrderDTO;
import com.bgu.se.ticketing.application.dto.TicketDTO;
import com.bgu.se.ticketing.domain.models.Order;
import com.bgu.se.ticketing.domain.models.Ticket;
import com.bgu.se.ticketing.domain.repositories.IOrderRepository;
import com.bgu.se.ticketing.domain.services.TicketReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Application service (use-case) for order lifecycle management.
 *
 * <p>Coordinates the domain and infrastructure layers. Contains no business logic –
 * orchestration only.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final IOrderRepository orderRepository;
    private final TicketReservationService ticketReservationService;

    public OrderService(IOrderRepository orderRepository,
                        TicketReservationService ticketReservationService) {
        this.orderRepository = orderRepository;
        this.ticketReservationService = ticketReservationService;
    }

    /**
     * Creates a new order, reserves the requested number of tickets and persists the order.
     *
     * @param request the creation request with buyerId, eventId and quantity
     * @return the persisted order as a DTO
     */
    public OrderDTO createOrder(CreateOrderRequestDTO request) {
        Order order = Order.create(request.getBuyerId(), request.getEventId());
        ticketReservationService.reserveTickets(order, request.getQuantity());
        Order saved = orderRepository.save(order);
        log.info("Created order {} for buyer {}", saved.getId(), saved.getBuyerId());
        return toDTO(saved);
    }

    /**
     * Confirms an existing order (e.g., after successful payment).
     *
     * @param orderId the ID of the order to confirm
     * @return the updated order as a DTO
     * @throws NoSuchElementException if the order does not exist
     */
    public OrderDTO confirmOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));
        order.confirm();
        Order saved = orderRepository.save(order);
        log.info("Confirmed order {}", saved.getId());
        return toDTO(saved);
    }

    /**
     * Cancels an existing order and releases its tickets back to the event.
     *
     * @param orderId the ID of the order to cancel
     * @return the updated order as a DTO
     * @throws NoSuchElementException if the order does not exist
     */
    public OrderDTO cancelOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));
        ticketReservationService.releaseTickets(order);
        order.cancel();
        Order saved = orderRepository.save(order);
        log.info("Cancelled order {}", saved.getId());
        return toDTO(saved);
    }

    /**
     * Retrieves all orders for a given buyer.
     *
     * @param buyerId the buyer's user ID
     * @return list of orders as DTOs
     */
    public List<OrderDTO> getOrdersByBuyer(String buyerId) {
        return orderRepository.findByBuyerId(buyerId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a single order by ID.
     *
     * @param orderId the order ID
     * @return the order as a DTO
     * @throws NoSuchElementException if the order does not exist
     */
    public OrderDTO getOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));
        return toDTO(order);
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private OrderDTO toDTO(Order order) {
        List<TicketDTO> ticketDTOs = order.getTickets().stream()
                .map(this::toTicketDTO)
                .collect(Collectors.toList());
        return new OrderDTO(
                order.getId(),
                order.getBuyerId(),
                order.getEventId(),
                ticketDTOs,
                order.getStatus(),
                order.calculateTotal(),
                order.getCreatedAt());
    }

    private TicketDTO toTicketDTO(Ticket ticket) {
        return new TicketDTO(ticket.getId(), ticket.getEventId(),
                ticket.getOwnerId(), ticket.getPrice(), ticket.getStatus());
    }
}
