package com.bgu.se.ticketing.api.controllers;

import com.bgu.se.ticketing.application.dto.EventDTO;
import com.bgu.se.ticketing.application.services.MarketService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for event browsing and management.
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final MarketService marketService;

    public EventController(MarketService marketService) {
        this.marketService = marketService;
    }

    /**
     * GET /api/events
     * Lists all available events. Public endpoint.
     */
    @GetMapping
    public ResponseEntity<List<EventDTO>> listEvents() {
        return ResponseEntity.ok(marketService.listAllEvents());
    }

    /**
     * GET /api/events/{id}
     * Retrieves a single event by ID. Public endpoint.
     */
    @GetMapping("/{id}")
    public ResponseEntity<EventDTO> getEvent(@PathVariable String id) {
        return ResponseEntity.ok(marketService.getEvent(id));
    }

    /**
     * POST /api/events
     * Creates a new event. Requires ORGANIZER or ADMIN role.
     */
    @PostMapping
    public ResponseEntity<EventDTO> createEvent(
            @RequestParam @NotBlank String name,
            @RequestParam(required = false) String description,
            @RequestParam @NotBlank String location,
            @RequestParam String eventDateTime,
            @RequestParam @Min(1) int totalCapacity,
            @RequestParam @DecimalMin("0.01") BigDecimal ticketPrice,
            @RequestParam @NotBlank String organizerId) {

        EventDTO event = marketService.createEvent(name, description, location,
                LocalDateTime.parse(eventDateTime), totalCapacity, ticketPrice, organizerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(event);
    }

    /**
     * GET /api/events?organizerId={organizerId}
     * Lists events created by a specific organizer.
     */
    @GetMapping("/organizer/{organizerId}")
    public ResponseEntity<List<EventDTO>> getEventsByOrganizer(@PathVariable String organizerId) {
        return ResponseEntity.ok(marketService.getEventsByOrganizer(organizerId));
    }
}
