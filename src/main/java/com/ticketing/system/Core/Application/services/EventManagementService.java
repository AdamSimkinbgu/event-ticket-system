package com.ticketing.system.Core.Application.services;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
// Owner / Manager-side write service for the Event aggregate and its lifecycle.
// UC-19 (Manage Event Catalog), UC-20 (Configure Venue Map & Inventory), UC-21 (Configure Policies).
import org.springframework.stereotype.Service;

import com.ticketing.system.Core.Application.dto.EventCreationDTO;
import com.ticketing.system.Core.Application.dto.EventDetailDTO;
import com.ticketing.system.Core.Application.dto.EventPolicyConfigDTO;
import com.ticketing.system.Core.Application.dto.EventUpdateDTO;
import com.ticketing.system.Core.Application.dto.VenueMapConfigDTO;
import com.ticketing.system.Core.Application.interfaces.IPaymentGateway;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.Tickets.TicketStatus;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.InventoryZone;
import com.ticketing.system.Core.Domain.events.StandingZone;
import com.ticketing.system.Core.Domain.events.VenueMap;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;
import com.ticketing.system.Core.Domain.orders.ReceiptLine;
import com.ticketing.system.Core.Domain.events.DiscountPolicy;
import com.ticketing.system.Core.Domain.events.PurchasePolicy;
import com.ticketing.system.Core.Domain.events.Seat;
import com.ticketing.system.Core.Domain.events.SeatedZone;

@Service
@Slf4j
public class EventManagementService {

    private final IEventRepository eventRepository;
    private final IProductionCompanyRepository companyRepository;
    private final ITicketRepository ticketRepository;
    private final ISessionManager sessionManager;
    private final IOrderReceiptRepository orderReceiptRepository;
    private final IPaymentGateway paymentGateway;

    public EventManagementService(
            IEventRepository eventRepository,
            IProductionCompanyRepository companyRepository,
            ITicketRepository ticketRepository,
            ISessionManager sessionManager,
            IOrderReceiptRepository orderReceiptRepository,
            IPaymentGateway paymentGateway
    ) {
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.ticketRepository = ticketRepository;
        this.sessionManager = sessionManager;
        this.orderReceiptRepository = orderReceiptRepository;
        this.paymentGateway = paymentGateway;
    }

    // flows:

    //addEvent(...)
    // → creates event with no zones / draft venue

    // configureVenueMap(...)
    // → creates StandingZone and SeatedZone objects from VenueMapConfigDTO
    // → attaches VenueMap to Event



    // UC-19 — Owner adds an Event in DRAFT state.
    public EventDetailDTO addEvent(String token, EventCreationDTO request) {
        if (!sessionManager.validateToken(token)) {
            log.warn("Invalid token provided for adding event");
            throw new RuntimeException("Invalid token");
        }

        int ownerId = sessionManager.extractUserId(token);
        ProductionCompany company = companyRepository.getCompanyById(request.companyId());
        if (company == null) {
            log.warn("Company {} not found", request.companyId());
            throw new RuntimeException("Company not found");
        }

        company.checkowner(ownerId);
        int newEventId = eventRepository.nextId();
        VenueMap venueMap = new VenueMap(3, request.location(), List.of());
        DiscountPolicy discountPolicy = new DiscountPolicy(10.0);   // Note: support policies later
        PurchasePolicy purchasePolicy = new PurchasePolicy(10);                  // Note: support policies later

        Event newEvent = new Event(
                newEventId, request.name(), 5.00, List.of("sss", "ddd"),
                request.category(), request.companyId(), EventStatus.SCHEDULED,
                venueMap, request.showDates(), purchasePolicy, discountPolicy);
        eventRepository.save(newEvent);

        log.info("Event {} created successfully with ID {}", request.name(), newEventId);
        return new EventDetailDTO(
                String.valueOf(newEventId),
                newEvent.getName(),
                newEvent.getRating(),
                request.description(),
                newEvent.getCategory(),
                request.location(),
                String.valueOf(newEvent.getCompanyId()),
                company.getName(),
                newEvent.getStatus(),
                newEvent.getShowDates());
    }

        


    // UC-19 — partial update; immutability rules per II.3.5.2 enforced inside Event.
    public void editEvent(String token, EventUpdateDTO update) {
        throw new UnsupportedOperationException("UC-19: not implemented");
    }





    // UC-19 — soft cancel; fires EventCancelled domain event for UC-4 refund pipeline.
    public void cancelEventAndRefund(String token, int eventId) {

        if (!sessionManager.validateToken(token)) {
            log.warn("Invalid token provided for inviting manager");
            throw new RuntimeException("Invalid token");
        }
        int ownerId = sessionManager.extractUserId(token);
        Event event = eventRepository.findById(eventId);
        if (event == null) {
            log.warn("Event {} not found", eventId);
            throw new RuntimeException("Event not found");
        }

        if (event.getStatus() == EventStatus.CANCELED) {
            log.warn("Event {} is already canceled", eventId);
            return;
        }

        ProductionCompany company = companyRepository.getCompanyById(event.getCompanyId());
        if (company == null) {
            log.warn("Company {} not found", event.getCompanyId());
            throw new RuntimeException("Company not found");
        }
        company.checkowner(ownerId);

        List<Ticket> tickets = ticketRepository.findByEventId(String.valueOf(eventId));
        List<OrderReceipt> orderReceipts = orderReceiptRepository.findByEventId(eventId);
        for (OrderReceipt receipt : orderReceipts) {
            if (receipt.wasRefunded()) {
                continue;
            }

            double totalRefundForReceipt = 0.0;
            boolean requiresRefund = false;

            for (ReceiptLine line : receipt.getReceiptLines()) {
                if (line.getEventId() == eventId) {
                    totalRefundForReceipt += line.getPriceAtReservation();
                    requiresRefund = true;
                }
            }

            if (requiresRefund && totalRefundForReceipt > 0) {
                // paymentGateway.refund(
                //     receipt.getHolderUserId(), 
                //      totalRefundForReceipt 
                //     // "Refund for canceled event: " + event.getId()
                // );
                //TODO: do the refund through the payment gateway and handle potential failures
                receipt.markRefunded();
                orderReceiptRepository.save(receipt);
            }
        }

        for (Ticket ticket : tickets) {
            if (ticket.getEventId() == eventId
                    && (ticket.getStatus() == TicketStatus.PAID || ticket.getStatus() == TicketStatus.ISSUED)) {
                ticket.markRefunded();
                ticketRepository.save(ticket);
            } else {
                ticket.markVoided();
                ticketRepository.save(ticket);
            }
        }

        // event.setCanceled(true);
        event.transitionToCanceled("Event canceled by owner/manager");
        eventRepository.save(event);

        log.info("Event {} canceled successfully", eventId);

    }






    public void configureVenueMap(String token, int companyId, VenueMapConfigDTO config) {
        if (!sessionManager.validateToken(token)) {
            throw new RuntimeException("Invalid token");
        }

        int userId = sessionManager.extractUserId(token);
        log.info("Configuring venue map for company {}, event {}, by user {}", companyId, config.eventId(), userId);

        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            throw new RuntimeException("Company not found");
        }

        company.ValidateManagerOrOwnerForConfigureVenue(userId);

        Event event = eventRepository.findById(Integer.parseInt(config.eventId()));
        if (event == null) {
            throw new RuntimeException("Event not found");
        }

        List<InventoryZone> zones = new ArrayList<>();
        int nextZoneId = 1;

        for (VenueMapConfigDTO.ZoneConfigDTO zoneConfig : config.zones()) {
            if (zoneConfig.seated()) {

                if (zoneConfig.seats() == null || zoneConfig.seats().isEmpty()) {
                    throw new IllegalArgumentException("Seated zone must contain seats");
                }

                List<Seat> seats = zoneConfig.seats().stream()
                        .map(seatConfig -> new Seat(seatConfig.label(), seatConfig.x(), seatConfig.y()))
                        .toList();

                zones.add(new SeatedZone(
                        nextZoneId,
                        zoneConfig.zoneName(),
                        zoneConfig.pricePerTicket(),
                        seats
                ));
            } else {

                if (zoneConfig.capacity() == null || zoneConfig.capacity() <= 0) {
                    throw new IllegalArgumentException("Standing zone capacity must be positive");
                }

                zones.add(new StandingZone(
                        nextZoneId,
                        zoneConfig.zoneName(),
                        zoneConfig.capacity(),
                        zoneConfig.pricePerTicket()
                ));
            }

            nextZoneId++;
        }

        VenueMap venueMap = new VenueMap(
                event.getVenueMap() != null ? event.getVenueMap().getId() : 1,
                event.getVenueMap() != null ? event.getVenueMap().getLocation() : null,
                zones
        );

        event.configureVenueMap(venueMap, companyId);
        eventRepository.save(event);
        log.info("Venue map for event {} configured successfully", event.getId());
    }
















    // UC-20 — Owner/Manager configures venue map and inventory zones.
    public void updateStandingZoneCapacity(String token, int company_id, int event_id, int zone_id, int newCapacity) {

        if (!sessionManager.validateToken(token)) {
            log.warn("Invalid token provided for updating zone capacity");
            throw new RuntimeException("Invalid token");
        }
        int userId = sessionManager.extractUserId(token);
        ProductionCompany company = companyRepository.getCompanyById(company_id);
        if (company == null) {
            log.warn("Company {} not found", company_id);
            throw new RuntimeException("Company not found");
        }

        company.ValidateManagerOrOwnerForConfigureVenue(userId);

        Event event = eventRepository.findById(event_id);

        if (event == null) {
            log.warn("Event {} not found", event_id);
            throw new RuntimeException("Event not found");
        }

        event.updateStandingZoneCapacity(zone_id, newCapacity, company_id);

        eventRepository.save(event);
        log.info("Zone {} at company {} capacity updated successfully", zone_id, company_id);

    }
    


    //TODO: might need to implement in EventManagementService or in Event, or we can say seated layout is immutable after venue configuration.
    // public void addSeatToSeatedZone(...){
            //TODO: might need to add a seat ID generator in VenueMap or SeatedZone to ensure unique seat IDs within the zone; or we can require the client to provide unique seat IDs in the request.
    // }


    // public void removeSeatFromSeatedZone(...){
            //TODO: might need to check if the seat is already reserved/sold before allowing removal, and handle that accordingly (e.g. prevent removal, or allow removal but mark any affected reservations as invalid and notify users, etc.)
    // }





    // UC-21 — set / replace event-level purchase + discount policies.
    public void setEventPolicies(String token, EventPolicyConfigDTO config) {
        throw new UnsupportedOperationException("UC-21: not implemented");
    }



    // Detail view for owner-side editing pages.
    public EventDetailDTO getEventDetail(String token, String eventId) {
        throw new UnsupportedOperationException("UC-19: not implemented");
    }
}
