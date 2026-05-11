package com.ticketing.system.Core.Application.dto;

import java.time.LocalDateTime;
import java.util.List;

// Input to EventManagementService.editEvent() (UC-19).
// All fields nullable — represents a partial update.
// The service applies the immutability rules (II.3.5.2) per field — see open question #9.
public record EventUpdateDTO(
    String eventId,
    String name,                        // nullable
    String description,                 // nullable
    String category,                    // nullable
    String location,                    // nullable
    List<LocalDateTime> showDates       // nullable — null means leave alone, empty means remove all
) {}
