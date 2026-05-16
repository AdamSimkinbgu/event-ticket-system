package com.ticketing.system.Core.Application.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// Owner / Manager-side write service for the Event aggregate and its lifecycle.
// UC-19 (Manage Event Catalog), UC-20 (Configure Venue Map & Inventory), UC-21 (Configure Policies).
import org.springframework.stereotype.Service;

import com.ticketing.system.Core.Application.dto.EventCreationDTO;
import com.ticketing.system.Core.Application.dto.EventDetailDTO;
import com.ticketing.system.Core.Application.dto.EventPolicyConfigDTO;
import com.ticketing.system.Core.Application.dto.EventUpdateDTO;
import com.ticketing.system.Core.Application.interfaces.IPaymentGateway;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.Tickets.TicketStatus;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;
import com.ticketing.system.Core.Domain.orders.ReceiptLine;

@Service
public class EventManagementService {

    private final IEventRepository eventRepository;
    private final IProductionCompanyRepository companyRepository;
    private final ITicketRepository ticketRepository;
    private final ISessionManager sessionManager;
    private final IOrderReceiptRepository orderReceiptRepository;
    private final IPaymentGateway paymentGateway;
    private static final Logger log = LoggerFactory.getLogger(EventManagementService.class);
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

    // UC-19 — Owner adds an Event in DRAFT state.
    public EventDetailDTO addEvent(String token, EventCreationDTO request) {
        throw new UnsupportedOperationException("UC-19: not implemented");
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
        if (event.isCancelled()) {
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
        List<OrderReceipt> orderReceipts = orderReceiptRepository.findByEventId(String.valueOf(eventId));
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
                paymentGateway.refund(
                    receipt.getHolderUserId(), 
                     totalRefundForReceipt 
                    // "Refund for canceled event: " + event.getId()
                );
                
                receipt.markRefunded();
                orderReceiptRepository.save(receipt);
            }
        }

        for(Ticket ticket : tickets){
            if(ticket.getEventId() == eventId &&  (ticket.getStatus() == TicketStatus.PAID || ticket.getStatus() == TicketStatus.ISSUED)){
                ticket.markRefunded();
                ticketRepository.save(ticket);
            }else{
                ticket.markVoided();
                ticketRepository.save(ticket);
            }
        }


        event.setCanceled(true);
        eventRepository.save(event);

        log.info("Event {} canceled successfully", eventId);
    
        
    }

    // UC-20 — Owner/Manager configures venue map and inventory zones.
    public void addCapacitoesToVenueMapZone(String token, int company_id,int event_id, int zone_id, int newCapacity) {
       
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

        company.ValidateManagerOrOwner(userId);

        Event event = eventRepository.findById(event_id);

        if (event == null) {
            log.warn("Event {} not found", event_id);
            throw new RuntimeException("Event not found");
        }

        event.updateZoneCapacity(zone_id, newCapacity, company_id);

        eventRepository.save(event);
        log.info("Zone {} at company {} capacity updated successfully", zone_id, company_id);

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
