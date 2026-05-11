package com.ticketing.system.Core.Application.services;

import java.util.List;

import com.ticketing.system.Core.Application.dto.EventCreationDTO;
import com.ticketing.system.Core.Application.dto.EventDetailDTO;
import com.ticketing.system.Core.Application.dto.EventPolicyConfigDTO;
import com.ticketing.system.Core.Application.dto.EventUpdateDTO;
import com.ticketing.system.Core.Application.dto.VenueMapConfigDTO;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.InventoryZone;

// Owner / Manager-side write service for the Event aggregate and its lifecycle.
// UC-19 (Manage Event Catalog), UC-20 (Configure Venue Map & Inventory), UC-21 (Configure Policies).
public class EventManagementService {

    private final IEventRepository eventRepository;
    private final IProductionCompanyRepository companyRepository;
    private final ITicketRepository ticketRepository;
    private final AuthenticationService authenticationService;

    public EventManagementService(
            IEventRepository eventRepository,
            IProductionCompanyRepository companyRepository,
            ITicketRepository ticketRepository,
            AuthenticationService authenticationService
    ) {
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.ticketRepository = ticketRepository;
        this.authenticationService = authenticationService;
    }

    // UC-19 — Owner adds an Event in DRAFT state.
    public EventDetailDTO addEvent(String token, EventCreationDTO request) {
        throw new UnsupportedOperationException("UC-19: not implemented");
    }

    // UC-19 — partial update; immutability rules per II.3.5.2 enforced inside Event.
    public void editEvent(String token, EventUpdateDTO update) {
        throw new UnsupportedOperationException("UC-19: not implemented");
    }

    // UC-19 — soft cancel; fires EventCancelled domain event for UC-4 refund pipeline.
    public void cancelEvent(String token, String eventId, String reason) {
        throw new UnsupportedOperationException("UC-19 / UC-4: not implemented");
    }

    // UC-20 — bind a VenueMap to an Event; pre-generates Tickets per the unified-Ticket model.
    public void configureVenueMap(String token, int companyID, int eventId, List<InventoryZone> inventoryZones ) {

        if (!authenticationService.validateToken(token)) {
            throw new RuntimeException("Invalid token");
        }

        int userId = authenticationService.extractUserId(token);
        
        ProductionCompany company = companyRepository.getCompanyById(companyID);
        
        if (company == null) {
                throw new RuntimeException("Company not found");
        }
        
        Event event = eventRepository.findById(String.valueOf(eventId));
        
        if (event == null) {
            throw new RuntimeException("Event not found");
        }

        company.ValidateManagerOrOwner(userId);

        event.ConfigureEvent(inventoryZones);
       
        eventRepository.save(event);


    }

    // UC-21 — set / replace event-level purchase + discount policies.
    public void setEventPolicies(String token, EventPolicyConfigDTO config) {
        throw new UnsupportedOperationException("UC-21: not implemented");
    }

    // Detail view for owner-side editing pages.
    public EventDetailDTO getEventDetail(String token, String eventId) {
        throw new UnsupportedOperationException("UC-19: not implemented");
    }
}
