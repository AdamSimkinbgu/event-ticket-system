package com.ticketing.system.Core.Application.dto;

import java.time.LocalDate;

// Input to CatalogService.searchGlobal() / searchByCompany() (UC-7).
// All fields nullable — represents an absent filter.
// 'minRating' is excluded from company-scoped search per II.2.3.2.
public record CatalogSearchFiltersDTO(
    String eventName,
    String artistName,
    String category,
    String keywords,
    Double minPrice,
    Double maxPrice,
    LocalDate fromDate,
    LocalDate toDate,
    String location,
    Double minRating, 
    Double maxRating
) {}
