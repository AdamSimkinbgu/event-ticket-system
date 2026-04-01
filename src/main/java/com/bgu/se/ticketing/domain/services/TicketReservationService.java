package com.bgu.se.ticketing.domain.services;

import com.bgu.se.ticketing.domain.models.Event;
import com.bgu.se.ticketing.domain.models.Order;
import com.bgu.se.ticketing.domain.models.Ticket;
import com.bgu.se.ticketing.domain.repositories.IEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Domain service that coordinates cross-aggregate logic for ticket reservations.
 *
 * <p>This service does not depend on any Spring or infrastructure framework; it
 * operates purely on domain objects and uses domain repository interfaces for any
 * data access it needs. It is instantiated and called by the Application layer.
 */
public class TicketReservationService {

    private static final Logger log = LoggerFactory.getLogger(TicketReservationService.class);

    private final IEventRepository eventRepository;

    public TicketReservationService(IEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    /**
     * Reserves {@code quantity} tickets for the given order, updating the event's
     * available-ticket count and attaching the new {@link Ticket} instances to the order.
     *
     * @param order    the PENDING order to add tickets to
     * @param quantity the number of tickets to reserve
     * @return the list of newly created tickets
     * @throws NoSuchElementException if the event does not exist
     * @throws IllegalStateException  if not enough tickets are available
     */
    public List<Ticket> reserveTickets(Order order, int quantity) {
        Event event = eventRepository.findById(order.getEventId())
                .orElseThrow(() -> new NoSuchElementException("Event not found: " + order.getEventId()));

        event.reserveTickets(quantity);
        eventRepository.save(event);

        List<Ticket> reserved = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            Ticket ticket = Ticket.create(event.getId(), order.getBuyerId(), event.getTicketPrice());
            order.addTicket(ticket);
            reserved.add(ticket);
        }

        log.debug("Reserved {} ticket(s) for order {} on event {}", quantity, order.getId(), event.getId());
        return reserved;
    }

    /**
     * Releases all tickets attached to an order back to the event's inventory.
     *
     * @param order the order whose tickets are to be released
     * @throws NoSuchElementException if the event does not exist
     */
    public void releaseTickets(Order order) {
        Event event = eventRepository.findById(order.getEventId())
                .orElseThrow(() -> new NoSuchElementException("Event not found: " + order.getEventId()));

        int count = order.getTickets().size();
        event.releaseTickets(count);
        eventRepository.save(event);

        log.debug("Released {} ticket(s) from order {} on event {}", count, order.getId(), event.getId());
    }
}
