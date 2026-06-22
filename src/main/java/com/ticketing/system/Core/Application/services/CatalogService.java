package com.ticketing.system.Core.Application.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import com.ticketing.system.Core.Domain.exceptions.*;

import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Domain.events.ShowDate;
import com.ticketing.system.Core.Application.dto.CatalogSearchFiltersDTO;
import com.ticketing.system.Core.Application.dto.EventSummaryDTO;
import com.ticketing.system.Core.Application.dto.VenueMapDTO;
import com.ticketing.system.Core.Application.dtoMappers.VenueMapMapper;
import com.ticketing.system.Core.Application.dtoMappers.EventMapper;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.company.CompanyStatus;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.events.IEventRepository;

// Read-side service for guest- and visitor-facing catalog queries.
// Owns UC-3 (Browse Events as Guest), UC-7 (Browse + Search Catalogs), UC-8 (View Venue Map + Inventory).
// Separated from EventManagementService (which is owner-side / write-heavy) so the two audiences
// don't share an API surface — see design_walkthrough_summary.md §6.
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CatalogService {

    private final ISessionManager sessionManager;
    private final IEventRepository eventRepository;
    private final IProductionCompanyRepository productionCompanyRepository;
    private final ITicketRepository ticketRepository;

    public CatalogService(
            ISessionManager sessionManager,
            IEventRepository eventRepository,
            IProductionCompanyRepository productionCompanyRepository,
            ITicketRepository ticketRepository
    ) {
        this.sessionManager = sessionManager;
        this.eventRepository = eventRepository;
        this.productionCompanyRepository = productionCompanyRepository;
        this.ticketRepository = ticketRepository;
    }



    //* UC-3: Browse the catalog, returning a list of EventSummaryDTOs. Accepts either a JWT (Member) or a raw sessionId (Guest) — catalog 
    //* browsing is open to anyone with an active session per UC-3 / UC-7 (auth rework #181 / Phase 4.3).
    public List<EventSummaryDTO> browseEventCatalog() {
        EventMapper mapper = new EventMapper();
        // Return all events that are ON_SALE and associated with an ACTIVE company.
        return productionCompanyRepository.findActive().stream()
                .flatMap(company -> eventRepository.findActiveByCompany(company.getCompanyId()).stream())
                .filter(event -> event.getStatus() == EventStatus.ON_SALE)
                .map(event -> mapper.convertEventToEventSummaryDTO(event, productionCompanyRepository))
                .toList();
    }




    //* V2-LANDING-01: Featured events for the public landing page — the ON_SALE events from active companies,
    //* ordered best-rated first (events without a rating sort last), capped at {limit}. No credential required:
    //* the landing page is anonymous, like browseEventCatalog above. There is no curation flag on Event, so
    //* "featured" is a rating-based heuristic for now.
    public List<EventSummaryDTO> featured(int limit) {
        EventMapper mapper = new EventMapper();
        return productionCompanyRepository.findActive().stream()
                .flatMap(company -> eventRepository.findActiveByCompany(company.getCompanyId()).stream())
                .sorted(Comparator.comparing(Event::getRating, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(0, limit))
                .map(event -> mapper.convertEventToEventSummaryDTO(event, productionCompanyRepository))
                .toList();
    }




    //* V2-LANDING-01: "On sale soon" teaser for the landing page — SCHEDULED events (a venue map is bound but
    //* sales haven't opened) from active companies, soonest show date first, capped at {limit}. findActiveByCompany
    //* returns ON_SALE only, so we source these from findByStatus(SCHEDULED) and drop any whose company isn't active.
    public List<EventSummaryDTO> upcomingOnSale(int limit) {
        EventMapper mapper = new EventMapper();
        return eventRepository.findByStatus(EventStatus.SCHEDULED).stream()
                .filter(event -> activeCompanyOrNull(event.getCompanyId()) != null)
                .sorted(Comparator.comparing(this::earliestShowStart, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(Math.max(0, limit))
                .map(event -> mapper.convertEventToEventSummaryDTO(event, productionCompanyRepository))
                .toList();
    }

    // *HELPER METHOD* — earliest show start for an event, or null if it somehow has no show dates
    // (an Event invariant requires >=1, so the null branch only guards against future changes).
    private LocalDateTime earliestShowStart(Event event) {
        return event.getShowDates().stream()
                .map(ShowDate::getStartTime)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }




    //* UC-7: Search the catalog with filters, returning a list of EventSummaryDTOs. Accepts either a JWT (Member) or a raw sessionId (Guest) — 
    //* catalog search is open to anyone with an active session per UC-3 / UC-7 (auth rework #181 / Phase 4.3).
    public List<EventSummaryDTO> searchGlobal(String credential, CatalogSearchFiltersDTO filters) {
        log.info("Starting global search with filters: {}", filters);
        // Validate the credential (JWT or sessionId) before proceeding with the search.
        if (!this.sessionManager.validateCredential(credential)) {
            log.warn("Invalid credential provided while performing global search with filters: {}", filters);
            throw new InvalidTokenException("Invalid credential provided while performing global search with filters: " + filters);
        }
        // Normalize and validate the filters, THROWING ---> IllegalArgumentException for invalid ranges or dates.
        CatalogSearchFiltersDTO effectiveFilters = normalizeAndValidateCatalogFilters(filters);
        // Create an EventMapper to convert Event entities to EventSummaryDTOs.
        EventMapper mapper = new EventMapper();

        List<EventSummaryDTO> results = new ArrayList<>();

        // Iterate over all events returned by the eventRepository.searchONSALE() method, applying the effective filters.
        for (Event event : eventRepository.searchONSALE(effectiveFilters)) {
            // Check if the event is associated with an active company and is publicly visible (ON_SALE).
            
            ProductionCompany company = activeCompanyOrNull(event.getCompanyId());

            // If the event is not publicly visible or the company does not match the company rating filters, skip to the next event.
            if (!isCompanyPubliclyVisible(company)) {
                continue;
            }
            if (!matchesCompanyRating(company, effectiveFilters)) {
                continue;
            }

            results.add(mapper.convertEventToEventSummaryDTO(event, productionCompanyRepository));
        }

        log.info("Global search with filters: {} returning {} results", effectiveFilters, results.size());
        return results;
    }





    //* the searchByCompany method is similar to searchGlobal but scoped to a specific company and without company-rating filters.
    public List<EventSummaryDTO> searchByCompany(String credential, int companyId, CatalogSearchFiltersDTO filters) {
        log.info("Starting company-scoped search for companyId: {} with filters: {}", companyId, filters);
        // Validate the credential (JWT or sessionId) before proceeding with the search.
        if (!this.sessionManager.validateCredential(credential)) {
            log.warn("Invalid credential provided while performing company-scoped search for companyId: {} with filters: {}",
                    companyId, filters);
            throw new InvalidTokenException("Invalid credential provided while performing company-scoped search for companyId: " + companyId);
        }

        // Validate that the specified company exists and is active, throwing CompanyNotFoundException or CompanyClosedException if not.
        requireActiveCompany(companyId);

        // Normalize and validate the filters, THROWING ---> IllegalArgumentException for invalid ranges or dates. 
        // Without company-rating filters since this is a company-scoped search.
        CatalogSearchFiltersDTO effectiveFilters = normalizeAndValidateCatalogFilters(filters).withoutCompanyRating();
        EventMapper mapper = new EventMapper();

        List<EventSummaryDTO> results = new ArrayList<>();

        for (Event event : eventRepository.searchONSALE(effectiveFilters)) {

            if (event.getCompanyId() != companyId) {
                continue;
            }
            // Check if the event is associated with an active company and is publicly visible (ON_SALE). this check is 
            ProductionCompany company = activeCompanyOrNull(event.getCompanyId());
            if (!isCompanyPubliclyVisible(company)) {
                continue;
            }

            results.add(mapper.convertEventToEventSummaryDTO(event, productionCompanyRepository));
        }

        log.info("Company-scoped search for companyId: {} with filters: {} returning {} results",
                companyId, effectiveFilters, results.size());

        return results;
    }

    






    // *HELPER METHOD* to normalize and validate the search filters, throwing IllegalArgumentException for invalid ranges or dates.
    private CatalogSearchFiltersDTO normalizeAndValidateCatalogFilters(CatalogSearchFiltersDTO filters) {
        CatalogSearchFiltersDTO f = filters == null
                ? CatalogSearchFiltersDTO.empty()
                : filters.normalized();

        validateDoubleRange(f.minPrice(), f.maxPrice(), "price");
        validateDoubleRange(f.minEventRating(), f.maxEventRating(), "event rating");
        validateDoubleRange(f.minCompanyRating(), f.maxCompanyRating(), "company rating");

        // validate that fromDate is before or equal to toDate if both are provided.
        if (f.fromDate() != null && f.toDate() != null && f.fromDate().isAfter(f.toDate())) {
            throw new IllegalArgumentException("fromDate must be before or equal to toDate");
        }

        return f;
    }

    // *HELPER METHOD* to validate that a min/max double range is valid, throwing IllegalArgumentException if min > max.
    private void validateDoubleRange(Double min, Double max, String name) {
        // if either min or max is null, we consider it unbounded and therefore valid, so only throw if both are non-null and min > max.
        if (min != null && max != null && min > max) {
            throw new IllegalArgumentException("min " + name + " must be <= max " + name);
        }
    }

    // *HELPER METHOD* to return the active ProductionCompany for a given companyId, or null if not found or not active.
    private ProductionCompany activeCompanyOrNull(int companyId) {
        try {
            ProductionCompany company = productionCompanyRepository.getCompanyById(companyId);
            // will throw if company not found, and if it doesn't throw but the company is not active, we'll return null to indicate that the company is not publicly visible.
            if (company == null || company.getStatus() != CompanyStatus.ACTIVE) {
                return null;
            }
            return company;
        } catch (RuntimeException e) {
            // If the company is not found or any other exception occurs, return null to indicate that the company does not exist.
            return null;
        }
    }

    // *HELPER METHOD* to require that a ProductionCompany exists and is active, throwing CompanyNotFoundException or CompanyClosedException if not.
    private ProductionCompany requireActiveCompany(int companyId) {
        ProductionCompany company;
        try {
            company = productionCompanyRepository.getCompanyById(companyId);
        } catch (RuntimeException e) {
            throw new CompanyNotFoundException(companyId);
        }

        if (company == null) {
            throw new CompanyNotFoundException(companyId);
        }

        if (company.getStatus() != CompanyStatus.ACTIVE) {
            throw new CompanyClosedException(companyId);
        }

        return company;
    }

    // *HELPER METHOD* to check if a ProductionCompany is publicly visible, i.e., ACTIVE.
    private boolean isCompanyPubliclyVisible(ProductionCompany company) {
        return company != null && company.getStatus() == CompanyStatus.ACTIVE;
    }

    // *HELPER METHOD* to check if a ProductionCompany's rating matches the min/max company rating filters, returning true if it does, false otherwise.
    private boolean matchesCompanyRating(ProductionCompany company, CatalogSearchFiltersDTO filters) {
        Double rating = company.getRating();

        if (filters.minCompanyRating() != null && (rating == null || rating < filters.minCompanyRating())) {
            return false;
        }

        if (filters.maxCompanyRating() != null && (rating == null || rating > filters.maxCompanyRating())) {
            return false;
        }

        return true;
    }




    






    // UC-8: Render venue map with per-seat / per-zone availability.
    // Accepts either a JWT (Member) or a raw sessionId (Guest) — venue map
    // viewing is open to anyone with an active session per UC-3 / UC-8
    // (auth rework #181 / Phase 4.3).
    public VenueMapDTO getEventVenueMap(String credential, int eventId) {
        log.info("Fetching venue map for eventId: {}", eventId);

            if (!this.sessionManager.validateCredential(credential)) {
                log.warn("Invalid credential provided while getting venue map for eventId: {}", eventId);
                throw new InvalidTokenException();
            }
            
            // see that event exists and has a venue map
            Event event = this.eventRepository.findById(eventId);
            if (event == null) {
                log.warn("Event not found while getting venue map: {}", eventId);
                throw new EventNotFoundException("Event with ID " + eventId + " not found while getting venue map");
            }

            // Additional check to enforce company status is ACTIVE.
            ProductionCompany company = productionCompanyRepository.getCompanyById(event.getCompanyId());
            if (company == null || company.getStatus() != CompanyStatus.ACTIVE) {
                log.warn("Attempt to access venue map for eventId: {} from inactive company", eventId);
                throw new CompanyClosedException("Event with ID " + eventId + " not found while getting venue map");
            }
            
            // see that event has a venue map
            if (event.getVenueMap() == null) {
                log.warn("Null venue map for event while getting venue map: {}", eventId);
                throw new NullVenueMapException(eventId);
            }

            log.info("Venue map found for eventId: {} while getting venue map and being returned", eventId);
            return new VenueMapMapper().venueMapToVenueMapDTO(event.getVenueMap());
        
    }
    
}
