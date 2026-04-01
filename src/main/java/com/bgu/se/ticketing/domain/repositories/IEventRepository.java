package com.bgu.se.ticketing.domain.repositories;

import com.bgu.se.ticketing.domain.models.Event;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository interface for {@link Event} aggregates.
 *
 * <p>Technology-agnostic. Implementations reside in the infrastructure layer.
 */
public interface IEventRepository {

    /** Persists a new event or updates an existing one. */
    Event save(Event event);

    /** Finds an event by its unique identifier. */
    Optional<Event> findById(String id);

    /** Returns all events in the system. */
    List<Event> findAll();

    /** Returns all events organized by a given organizer. */
    List<Event> findByOrganizerId(String organizerId);

    /** Deletes an event by its unique identifier. */
    void deleteById(String id);
}
