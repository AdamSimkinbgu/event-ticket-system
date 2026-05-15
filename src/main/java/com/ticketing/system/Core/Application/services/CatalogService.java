package com.ticketing.system.Core.Application.services;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.server.Session;

import com.ticketing.system.Core.Domain.exceptions.*;

import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Application.dto.CatalogSearchFiltersDTO;
import com.ticketing.system.Core.Application.dto.EventSummaryDTO;
import com.ticketing.system.Core.Application.dto.PageDTO;
import com.ticketing.system.Core.Application.dto.ShowDateDTO;
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
public class CatalogService {

    private static final Logger logger = LoggerFactory.getLogger(CatalogService.class);

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




    // UC-3: Browse Events as Guest. Lists active events from active production companies.
    public List<EventSummaryDTO> browseEventCatalog() {
        throw new UnsupportedOperationException("UC-3: not implemented");
    }








    // UC-7: Global search with filters (price/date/location/rating, plus name/artist/category/keywords).
    public List<EventSummaryDTO> searchGlobal(String token, CatalogSearchFiltersDTO filters) {
        logger.info("Starting global search with filters: {}", filters);
        if (!this.sessionManager.validateToken(token)) {
            logger.warn("Invalid Token provided while performing global search with filters: {}", filters);
            throw new InvalidTokenException();
        }

        ArrayList<EventSummaryDTO> results = new ArrayList<>();
        // get all events matching the search criteria
        List<Event> eventFilteration = eventRepository.search(filters);

        for (Event event : eventFilteration) {
            // company rating filter that is added in the global search but not the company-scoped search
            if (filters.minCompanyRating() != null && productionCompanyRepository.getCompanyById(event.getCompanyId())
                    .getRating() < filters.minCompanyRating()) {
                continue;
            }
            if (filters.maxCompanyRating() != null && productionCompanyRepository.getCompanyById(event.getCompanyId())
                    .getRating() > filters.maxCompanyRating()) {
                continue;
            }
            results.add(new EventMapper().convertEventToEventSummaryDTO(event, productionCompanyRepository));
        }
        logger.info("Global search with filters: {} returning {} results", filters, results.size());
        return results;
    }




    // UC-7: Company-scoped search (no rating filter per II.2.3.2).
    public List<EventSummaryDTO> searchByCompany(String token, int companyId, CatalogSearchFiltersDTO filters) {
        logger.info("Starting company-scoped search for companyId: {} with filters: {}", companyId, filters);
        if (!this.sessionManager.validateToken(token)) {
            logger.warn("Invalid Token provided while performing company-scoped search for companyId: {} with filters: {}", companyId, filters);
            throw new InvalidTokenException();
        }

        List<EventSummaryDTO> results = new ArrayList<>();
        List<Event> eventFilteration = eventRepository.search(filters);

        for (Event event : eventFilteration) {
            // only include this current company's events
            if (event.getCompanyId() != companyId) {
                continue;
            }
            results.add(new EventMapper().convertEventToEventSummaryDTO(event, productionCompanyRepository));
        }
        logger.info("Company-scoped search for companyId: {} with filters: {} returning {} results", companyId, filters, results.size());
        return results;
    }








    // UC-8: Render venue map with per-seat / per-zone availability.
    // Accepts either a JWT (Member) or a raw sessionId (Guest) — venue map
    // viewing is open to anyone with an active session per UC-3 / UC-8
    // (auth rework #181 / Phase 4.3).
    public VenueMapDTO getEventVenueMap(String credential, int eventId) {
        logger.info("Fetching venue map for eventId: {}", eventId);

            if (!this.sessionManager.validateCredential(credential)) {
                logger.warn("Invalid credential provided while getting venue map for eventId: {}", eventId);
                throw new InvalidTokenException();
            }
            
            // see that event exists and has a venue map
            Event event = this.eventRepository.findById(eventId);
            if (event == null) {
                logger.warn("Event not found while getting venue map: {}", eventId);
                throw new EventNotFoundException("Event with ID " + eventId + " not found while getting venue map");
            }

            // Additional check to enforce company status is ACTIVE.
            ProductionCompany company = productionCompanyRepository.getCompanyById(event.getCompanyId());
            if (company == null || company.getStatus() != CompanyStatus.ACTIVE) {
                logger.warn("Attempt to access venue map for eventId: {} from inactive company", eventId);
                throw new CompanyClosedException("Event with ID " + eventId + " not found while getting venue map");
            }
            
            // see that event has a venue map
            if (event.getVenueMap() == null) {
                logger.warn("Null venue map for event while getting venue map: {}", eventId);
                throw new NullVenueMapException(eventId);
            }

            logger.info("Venue map found for eventId: {} while getting venue map and being returned", eventId);
            return new VenueMapMapper().venueMapToVenueMapDTO(event.getVenueMap());
        
    }
    
}
