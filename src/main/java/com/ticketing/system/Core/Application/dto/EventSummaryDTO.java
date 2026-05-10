package com.ticketing.system.Core.Application.dto;

import java.time.LocalDateTime;
import java.util.List;

// Lightweight Event projection for listings — UC-3 (browse), UC-7 (search results).
// Use EventDetailDTO for the per-event detail page.
public record EventSummaryDTO(
    String eventId,
    String name,
    String companyName,
    String category,
    String location,
    List<LocalDateTime> showDates,
    double minPrice,
    double maxPrice,
    boolean soldOut
) {}
