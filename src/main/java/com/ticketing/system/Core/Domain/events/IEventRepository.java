package com.ticketing.system.Core.Domain.events;

import java.util.List;

import com.ticketing.system.Core.Application.dto.CatalogSearchFiltersDTO;

// Aggregate-root entry point for the Event aggregate.
public interface IEventRepository {

    Event findById(int eventId);

    boolean save(Event event);

    // UC-3 / UC-22 / UC-31 — events of a given company (by-ID cross-aggregate query).
    List<Event> findByCompanyId(int companyId);

    // UC-3 / UC-22 — fast id-only projection (avoids loading full Event aggregates).
    List<Integer> findIdsByCompany(int companyId);

    // UC-3 / UC-7 — public catalog uses ON_SALE + active company filter.
    List<Event> findActiveByCompany(int companyId);

    // UC-3 / UC-19 — events grouped by lifecycle state.
    List<Event> findByStatus(EventStatus status);

    // UC-7 — search across all events (filters from DTO; pagination handled in service).
    List<Event> search(CatalogSearchFiltersDTO filters);

    void cancelEvent(int eventId);
}