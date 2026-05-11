package com.ticketing.system.Core.Application.services;

import java.util.List;

import com.ticketing.system.Core.Application.dto.CatalogSearchFiltersDTO;
import com.ticketing.system.Core.Application.dto.EventSummaryDTO;
import com.ticketing.system.Core.Application.dto.PageDTO;
import com.ticketing.system.Core.Application.dto.VenueMapDTO;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.events.IEventRepository;

// Read-side service for guest- and visitor-facing catalog queries.
// Owns UC-3 (Browse Events as Guest), UC-7 (Browse + Search Catalogs), UC-8 (View Venue Map + Inventory).
// Separated from EventManagementService (which is owner-side / write-heavy) so the two audiences
// don't share an API surface — see design_walkthrough_summary.md §6.
public class CatalogService {

    private final IEventRepository eventRepository;
    private final IProductionCompanyRepository productionCompanyRepository;
    private final ITicketRepository ticketRepository;

    public CatalogService(
            IEventRepository eventRepository,
            IProductionCompanyRepository productionCompanyRepository,
            ITicketRepository ticketRepository
    ) {
        this.eventRepository = eventRepository;
        this.productionCompanyRepository = productionCompanyRepository;
        this.ticketRepository = ticketRepository;
    }

    // UC-3: Browse Events as Guest. Lists active events from active production companies.
    public List<EventSummaryDTO> browseEventCatalog() {
        throw new UnsupportedOperationException("UC-3: not implemented");
    }

    // UC-7: Global search with filters (price/date/location/rating, plus name/artist/category/keywords).
    public PageDTO<EventSummaryDTO> searchGlobal(CatalogSearchFiltersDTO filters, int pageNumber, int pageSize) {
        throw new UnsupportedOperationException("UC-7: searchGlobal not implemented");
    }

    // UC-7: Company-scoped search (no rating filter per II.2.3.2).
    public PageDTO<EventSummaryDTO> searchByCompany(int companyId, CatalogSearchFiltersDTO filters, int pageNumber, int pageSize) {
        throw new UnsupportedOperationException("UC-7: searchByCompany not implemented");
    }

    // UC-8: Render venue map with per-seat / per-zone availability.
    public VenueMapDTO getEventVenueMap(String eventId) {
        throw new UnsupportedOperationException("UC-8: not implemented");
    }
}
