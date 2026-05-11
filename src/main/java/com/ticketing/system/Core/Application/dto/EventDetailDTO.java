package com.ticketing.system.Core.Application.dto;

import java.time.LocalDateTime;
import java.util.List;

// Full Event detail — used when the catalog summary (EventSummaryDTO) isn't enough.
// UC-8 (alongside VenueMapDTO), UC-19 (Owner viewing their event for edit).
public record EventDetailDTO(
    String eventId,
    String name,
    String description,
    String category,
    String location,
    String companyId,
    String companyName,
    String status,                     // EventStatus value as string
    List<LocalDateTime> showDates,
    double minPrice,
    double maxPrice
) {}
