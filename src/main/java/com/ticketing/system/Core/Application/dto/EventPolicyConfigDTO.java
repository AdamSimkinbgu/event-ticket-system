package com.ticketing.system.Core.Application.dto;

import java.util.List;

// Input to EventManagementService.setEventPolicies() (UC-21 event-level).
public record EventPolicyConfigDTO(
    int companyId,
        int eventId,
      PurchasePolicyDTO purchasePolicy
) {}
