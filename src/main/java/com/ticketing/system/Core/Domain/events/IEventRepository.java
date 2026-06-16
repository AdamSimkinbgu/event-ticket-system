package com.ticketing.system.Core.Domain.events;

import java.util.List;

import com.ticketing.system.Core.Application.dto.CatalogSearchFiltersDTO;
import com.ticketing.system.Core.Domain.shared.IRepository;

// Aggregate-root entry point for the Event aggregate.
public interface IEventRepository extends IRepository<Event, Integer> {

    Event findById(int eventId);

    boolean save(Event event);

    /**
     * Shared lifecycle lock for buyer inventory operations.
     *
     * Many buyers may hold this lock for the same event at the same time.
     * It blocks structural/event-lifecycle writes such as venue editing, policy changes,
     * cancellation, publishing, etc.
     *
     * Actual inventory correctness is still protected by StandingZone / SeatedZone locks.
     */
    void lockForBuyerOperation(int eventId);

    /**
     * Releases a buyer lifecycle lock acquired by lockForBuyerOperation.
     */
    void unlockBuyerOperation(int eventId);

    // UC-3 / UC-22 / UC-31 — events of a given company (by-ID cross-aggregate query).
    List<Event> findByCompanyId(int companyId);

    // UC-3 / UC-22 — fast id-only projection (avoids loading full Event aggregates).
    List<Integer> findIdsByCompany(int companyId);

    // UC-3 / UC-7 — public catalog uses ON_SALE + active company filter.
    List<Event> findActiveByCompany(int companyId);

    // UC-3 / UC-19 — events grouped by lifecycle state.
    List<Event> findByStatus(EventStatus status);

    // search across all events (filters from DTO).
    List<Event> searchAll(CatalogSearchFiltersDTO filters);
    // UC-7 — search across all events that are ON_SALE (filters from DTO).
    List<Event> searchONSALE(CatalogSearchFiltersDTO filters);
    

    int nextId();

    int nextVenueMapId();
}