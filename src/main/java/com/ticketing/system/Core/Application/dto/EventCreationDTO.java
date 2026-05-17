package com.ticketing.system.Core.Application.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.ticketing.system.Core.Domain.events.EventCategory;
import com.ticketing.system.Core.Domain.events.Location;
import com.ticketing.system.Core.Domain.events.ShowDate;

// Input to EventManagementService.addEvent() (UC-19).
// Event lands in DRAFT state; UC-20 binds VenueMap and pre-generates Tickets.
public record EventCreationDTO(
    int companyId,
    String name,
    String description,
    EventCategory category,
    Location location,
    List<ShowDate> showDates
) {}
