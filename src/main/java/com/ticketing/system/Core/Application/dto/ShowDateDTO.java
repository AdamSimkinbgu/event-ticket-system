package com.ticketing.system.Core.Application.dto;

import java.time.LocalDateTime;

// One scheduled showing of an Event. Multiple ShowDates per Event per II.4.1.1.
public record ShowDateDTO(
    String showDateId,
    LocalDateTime startsAt,
    LocalDateTime endsAt,
    String status              // SCHEDULED / DELAYED / COMPLETED / CANCELED
) {}
