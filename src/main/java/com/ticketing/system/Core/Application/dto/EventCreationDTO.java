package com.ticketing.system.Core.Application.dto;

import java.util.List;

import com.ticketing.system.Core.Domain.events.EventCategory;
import com.ticketing.system.Core.Domain.events.Location;
import com.ticketing.system.Core.Domain.events.ShowDate;

public record EventCreationDTO(
        int companyId,
        String name,
        String description,
        List<String> artistsNames,
        EventCategory category,
        Double rating,                
        Location location,             // might need to be LocationDTO
        List<ShowDate> showDates,      // might need to be List<ShowDateDTO>
        PurchasePolicyDTO purchasePolicy
) {}