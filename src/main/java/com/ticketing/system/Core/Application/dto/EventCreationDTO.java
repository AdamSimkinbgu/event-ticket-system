package com.ticketing.system.Core.Application.dto;

import java.util.List;

import com.ticketing.system.Core.Domain.events.EventCategory;
import com.ticketing.system.Core.Domain.events.Location;
import com.ticketing.system.Core.Domain.events.ShowDate;

public record EventCreationDTO(
        int companyId,
        String name,
        String description,
        EventCategory category,
        Location location,
        List<ShowDate> showDates,
        PurchasePolicyDTO purchasePolicy
) {}