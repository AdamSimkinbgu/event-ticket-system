package com.ticketing.system.Core.Application.dto;

// General Production Company view.
// Used in catalog browse output (alongside EventSummaryDTO), org-tree responses,
// and any "view company" query.
public record ProductionCompanyDTO(
    int companyId,
    String name,
    String description,
    String status,                  // CompanyStatus value as string
    int founderId,
) {}
