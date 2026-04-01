package com.bgu.se.ticketing.domain.repositories;

import com.bgu.se.ticketing.domain.models.Ticket;
import com.bgu.se.ticketing.domain.models.TicketStatus;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository interface for {@link Ticket} entities.
 *
 * <p>Technology-agnostic. Implementations reside in the infrastructure layer.
 */
public interface ITicketRepository {

    /** Persists a new ticket or updates an existing one. */
    Ticket save(Ticket ticket);

    /** Finds a ticket by its unique identifier. */
    Optional<Ticket> findById(String id);

    /** Returns all tickets belonging to a given event. */
    List<Ticket> findByEventId(String eventId);

    /** Returns all tickets owned by a given user. */
    List<Ticket> findByOwnerId(String ownerId);

    /** Returns all tickets for an event filtered by status. */
    List<Ticket> findByEventIdAndStatus(String eventId, TicketStatus status);

    /** Deletes a ticket by its unique identifier. */
    void deleteById(String id);
}
