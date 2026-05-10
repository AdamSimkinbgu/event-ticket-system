package com.ticketing.system.Core.Application.dto;

// Lightweight Company projection for listings.
// Used in UC-3 catalog browse where Events are grouped by company.
public record CompanySummaryDTO(
    int companyId,
    String name,
    Double rating               // nullable — see open question #4 in design walkthrough
) {}
