package com.ticketing.system.Core.Application.dto;

import java.util.List;

public record PurchasePolicyDTO(
        String type,
        Integer minimumAge,
        Integer minimumTickets,
        Integer maximumTickets,
        List<PurchasePolicyDTO> children
) {}