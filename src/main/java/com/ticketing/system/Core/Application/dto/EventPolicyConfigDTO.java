package com.ticketing.system.Core.Application.dto;

import java.util.List;

// Input to EventManagementService.setEventPolicies() (UC-21 event-level).
public record EventPolicyConfigDTO(
    String eventId,
    PurchasePolicyDTO purchasePolicy,
    List<DiscountPolicyDTO> discountPolicies
) {}
