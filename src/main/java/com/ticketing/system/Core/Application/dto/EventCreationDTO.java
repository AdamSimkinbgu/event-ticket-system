package com.ticketing.system.Core.Application.dto;

import java.time.LocalDateTime;
import java.util.List;

// Input to EventManagementService.addEvent() (UC-19).
// Event lands in DRAFT state; UC-20 binds VenueMap and pre-generates Tickets.
public record EventCreationDTO(
    int companyId,
    String name,
    String description,
    String category,
    String location,
    List<LocalDateTime> showDates
) {}
