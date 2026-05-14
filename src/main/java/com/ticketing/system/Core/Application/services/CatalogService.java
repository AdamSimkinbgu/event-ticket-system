package com.ticketing.system.Core.Application.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.server.Session;

import com.ticketing.system.Core.Domain.exceptions.*;

import com.ticketing.system.Core.Domain.events.Event;

import com.ticketing.system.Core.Application.dto.CatalogSearchFiltersDTO;
import com.ticketing.system.Core.Application.dto.EventSummaryDTO;
import com.ticketing.system.Core.Application.dto.PageDTO;
import com.ticketing.system.Core.Application.dto.VenueMapDTO;
import com.ticketing.system.Core.Application.dtoMappers.VenueMapMapper;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
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
    public PageDTO<EventSummaryDTO> searchGlobal(CatalogSearchFiltersDTO filters, int pageNumber, int pageSize) {
        //Note: location, minRating, maxRating — not modelled on Event; not filtered here in the IEventRepository search implementation.
        // Would require additional implementation to implement filter properly.
        throw new UnsupportedOperationException("UC-7: searchGlobal not implemented");
    }




    // UC-7: Company-scoped search (no rating filter per II.2.3.2).
    public PageDTO<EventSummaryDTO> searchByCompany(int companyId, CatalogSearchFiltersDTO filters, int pageNumber, int pageSize) {
        //Note: location, minRating, maxRating — not modelled on Event; not filtered here in the IEventRepository search implementation.
        // Would require additional implementation to implement filter properly.
        throw new UnsupportedOperationException("UC-7: searchByCompany not implemented");
    }





    // UC-8: Render venue map with per-seat / per-zone availability.
    public VenueMapDTO getEventVenueMap(String token, int eventId) {
        logger.info("Fetching venue map for eventId: {}", eventId);
        try {
            if (!this.sessionManager.validateToken(token)) {
                throw new SessionExpiredException();
            }
            
            Event event = this.eventRepository.findById(eventId);
            if (event == null) {
                throw new EventNotFoundException("Event with ID " + eventId + " not found while getting venue map");
            }
            
            if (event.getVenueMap() == null) {
                throw new NullVenueMapException(eventId);
            }
            logger.info("Venue map found for eventId: {} while getting venue map and being returned", eventId);
            return new VenueMapMapper().venueMapToVenueMapDTO(event.getVenueMap());

        } catch (SessionExpiredException e) {
            logger.warn("Session expired for token: {}", token);
            throw e; // rethrowing for now to avoid swallowing exceptions during development
        } catch (EventNotFoundException e) {
            logger.warn("Event not found while getting venue map: {}", eventId);
            throw e; // rethrowing for now to avoid swallowing exceptions during development
        } catch (NullVenueMapException e) {
            logger.warn("Null venue map for event while getting venue map: {}", eventId);
            throw e; // rethrowing for now to avoid swallowing exceptions during development
        } catch (Exception e) {
            logger.error("Unexpected error while fetching venue map for event: {}", eventId, e);
            throw e; // rethrowing for now to avoid swallowing exceptions during development
        }
        
    }
}
