package com.ticketing.system.Core.Application.dto;

// Configures purchase rules per II.4.3.1.
// Used for both event-level (UC-21 setEventPolicies) and company-level (UC-21 setCompanyPolicies).
public record PurchasePolicyDTO(
    Integer maxTicketsPerBuyer,         // nullable = unlimited
    Integer minAge,                     // nullable = no age restriction
    String saleMethod                   // "STANDARD" or "LOTTERY" per II.4.3.1
) {}
