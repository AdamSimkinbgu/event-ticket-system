package com.bgu.se.ticketing.infrastructure.persistence;

import com.bgu.se.ticketing.domain.models.Order;
import com.bgu.se.ticketing.domain.models.OrderStatus;
import com.bgu.se.ticketing.domain.models.Ticket;
import com.bgu.se.ticketing.domain.repositories.IOrderRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Infrastructure implementation of {@link IOrderRepository} backed by Spring Data JPA.
 *
 * <p>This implementation acts as the Anti-Corruption Layer: it translates between the
 * rich domain {@link Order} aggregate (which owns a list of {@link Ticket} objects)
 * and the flat JPA model (which stores ticket IDs).
 */
@Component
public class OrderRepositoryImpl implements IOrderRepository {

    private final SpringOrderRepository springOrderRepo;
    private final TicketRepositoryImpl ticketRepository;

    public OrderRepositoryImpl(SpringOrderRepository springOrderRepo,
                               TicketRepositoryImpl ticketRepository) {
        this.springOrderRepo = springOrderRepo;
        this.ticketRepository = ticketRepository;
    }

    @Override
    public Order save(Order order) {
        // Persist each ticket and collect its ID
        List<String> ticketIds = order.getTickets().stream()
                .map(ticket -> ticketRepository.saveWithOrder(ticket, order.getId()).getId())
                .collect(Collectors.toList());

        // Build or update the order entity
        OrderJpaEntity entity = springOrderRepo.findById(order.getId())
                .orElse(new OrderJpaEntity(order.getId(), order.getBuyerId(),
                        order.getEventId(), order.getStatus(), order.getCreatedAt()));
        entity.setStatus(order.getStatus());
        entity.setTicketIds(ticketIds);
        OrderJpaEntity saved = springOrderRepo.save(entity);

        return reconstruct(saved);
    }

    @Override
    public Optional<Order> findById(String id) {
        return springOrderRepo.findById(id).map(this::reconstruct);
    }

    @Override
    public List<Order> findByBuyerId(String buyerId) {
        return springOrderRepo.findByBuyerId(buyerId).stream()
                .map(this::reconstruct).collect(Collectors.toList());
    }

    @Override
    public List<Order> findByEventId(String eventId) {
        return springOrderRepo.findByEventId(eventId).stream()
                .map(this::reconstruct).collect(Collectors.toList());
    }

    @Override
    public List<Order> findByStatus(OrderStatus status) {
        return springOrderRepo.findByStatus(status).stream()
                .map(this::reconstruct).collect(Collectors.toList());
    }

    @Override
    public void deleteById(String id) {
        springOrderRepo.deleteById(id);
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    /**
     * Reconstructs a domain {@link Order} from a persisted {@link OrderJpaEntity} by
     * loading each ticket from the ticket repository.
     */
    private Order reconstruct(OrderJpaEntity entity) {
        Order order = new Order(entity.getId(), entity.getBuyerId(), entity.getEventId(),
                entity.getStatus(), entity.getCreatedAt());
        entity.getTicketIds().stream()
                .map(ticketRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(order::addTicket);
        return order;
    }
}
