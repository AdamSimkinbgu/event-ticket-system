package com.ticketing.system.Core.Application.dto;

import java.util.List;

import com.ticketing.system.Core.Domain.events.EventCategory;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Domain.events.Location;
import com.ticketing.system.Core.Domain.events.ShowDate;

// Full Event detail — used when the catalog summary (EventSummaryDTO) isn't enough.
// UC-8 (alongside VenueMapDTO), UC-19 (Owner viewing their event for edit).
public record EventDetailDTO(
        String eventId,
        String name,
        Double rating,
        String description,
        EventCategory category,
        Location location,
        String companyId,
        String companyName,
        EventStatus status, // EventStatus value as string
        List<ShowDate> showDates,
        List<String> artistsNames // lineup (Event.getArtistsNames())
) {
}
