package com.bgu.se.ticketing.application.services;

import com.bgu.se.ticketing.application.dto.EventDTO;
import com.bgu.se.ticketing.domain.models.Event;
import com.bgu.se.ticketing.domain.repositories.IEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Application service (use-case) for browsing and managing events on the market.
 *
 * <p>Coordinates the domain and infrastructure layers. Contains no business logic –
 * orchestration only.
 */
@Service
public class MarketService {

    private static final Logger log = LoggerFactory.getLogger(MarketService.class);

    private final IEventRepository eventRepository;

    public MarketService(IEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    /**
     * Creates and publishes a new event.
     *
     * @param name          event name
     * @param description   optional description
     * @param location      venue or location
     * @param eventDateTime date and time of the event
     * @param totalCapacity total number of tickets available
     * @param ticketPrice   price per ticket
     * @param organizerId   ID of the organizer (User aggregate)
     * @return the created event as a DTO
     */
    public EventDTO createEvent(String name, String description, String location,
                                LocalDateTime eventDateTime, int totalCapacity,
                                BigDecimal ticketPrice, String organizerId) {
        Event event = Event.create(name, description, location, eventDateTime,
                totalCapacity, ticketPrice, organizerId);
        Event saved = eventRepository.save(event);
        log.info("Created event '{}' by organizer {}", saved.getName(), saved.getOrganizerId());
        return toDTO(saved);
    }

    /**
     * Returns all available events.
     *
     * @return list of all events as DTOs
     */
    public List<EventDTO> listAllEvents() {
        return eventRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a single event by ID.
     *
     * @param eventId the event ID
     * @return the event as a DTO
     * @throws NoSuchElementException if the event is not found
     */
    public EventDTO getEvent(String eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NoSuchElementException("Event not found: " + eventId));
        return toDTO(event);
    }

    /**
     * Retrieves all events created by a specific organizer.
     *
     * @param organizerId the organizer's user ID
     * @return list of matching events as DTOs
     */
    public List<EventDTO> getEventsByOrganizer(String organizerId) {
        return eventRepository.findByOrganizerId(organizerId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private EventDTO toDTO(Event event) {
        return new EventDTO(
                event.getId(),
                event.getName(),
                event.getDescription(),
                event.getLocation(),
                event.getEventDateTime(),
                event.getTotalCapacity(),
                event.getAvailableTickets(),
                event.getTicketPrice(),
                event.getOrganizerId());
    }
}
