package com.ticketing.system.Core.Application.services;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
// Owner / Manager-side write service for the Event aggregate and its lifecycle.
// UC-19 (Manage Event Catalog), UC-20 (Configure Venue Map & Inventory), UC-21 (Configure Policies).
import org.springframework.stereotype.Service;

import com.ticketing.system.Core.Application.dto.RefundResultDTO;
import com.ticketing.system.Core.Domain.exceptions.RefundFailedException;
import com.ticketing.system.Core.Domain.orders.TransactionRecord;
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

    
    // Flow:

    // addEvent adds an event in DRAFT state; venue map and inventory configuration come later in UC-20.
    
    // Note: we could combine addEvent and configureVenueMap into a single UC-19 method that takes a more complex DTO, but separating them allows for a cleaner separation of concerns and 
    // more focused validation (e.g. addEvent doesn't need to worry about venue map details at all).

    // configureVenueMap is a separate method that can be called multiple times to update the venue map and inventory zones *before* the event goes live. 
    // It also allows for a more iterative setup process where the owner/manager can first create the event with basic details and then configure the venue map in a second step.



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
        VenueMap venueMap = new VenueMap(1, request.location(), List.of()); //TODO: need an incremantal ID counter for venue maps
        DiscountPolicy discountPolicy = new DiscountPolicy(10.0); // Note: support policies later
        PurchasePolicy purchasePolicy = new PurchasePolicy(10); // Note: support policies later

        Event newEvent = new Event(
                newEventId, request.name(), 5.00, List.of("sss", "ddd"),
                request.category(), request.companyId(), EventStatus.DRAFT,
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
    












    // configureVenueMap is a separate method that can be called multiple times to update the venue map and inventory zones *before* the event goes live. 
    // It also allows for a more iterative setup process where the owner/manager can first create the event with basic details and then configure the venue map in a second step.
    
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









    







        


    // UC-19 — partial update; immutability rules per II.3.5.2 enforced inside Event.
    public void editEventDetails(String token, EventUpdateDTO update) {
        throw new UnsupportedOperationException("UC-19: not implemented");
    }





    // UC-19 — soft cancel; fires EventCancelled domain event for UC-4 refund pipeline.
    public void cancelEventAndRefund(String token, int eventId) {

        if (!sessionManager.validateToken(token)) {
            throw new RuntimeException("Invalid token");
        }

        int ownerId = sessionManager.extractUserId(token);
        // We lock the event for update to prevent concurrent modifications during the cancellation and refund process. This ensures that we have a consistent view of the event's state
        eventRepository.lockForUpdate(eventId);

        try {
            Event event = eventRepository.findById(eventId);
            if (event == null) {
                throw new RuntimeException("Event not found");
            }

            if (event.getStatus() == EventStatus.CANCELED) {
                return;
            }
            // Authorization: only the owner of the event can cancel it and trigger refunds. We enforce this by checking the requester’s user ID against the owner ID of the production company that owns the event. If the requester is not the owner, we throw an exception and abort the cancellation process.
            ProductionCompany company = companyRepository.getCompanyById(event.getCompanyId());
            if (company == null) {
                throw new RuntimeException("Company not found");
            }

            company.checkowner(ownerId);

            List<OrderReceipt> orderReceipts = orderReceiptRepository.findByEventId(eventId);
            // For each receipt, we calculate the total refund amount for the lines related to the canceled event, call the payment gateway to process the refund, validate the refund result, and then update our domain state accordingly (marking the receipt as refunded and updating ticket statuses). We also handle various edge cases and potential errors along the way to ensure a robust refund process.
            for (OrderReceipt receipt : orderReceipts) {
                double totalRefundForReceipt = receipt.getReceiptLines().stream()
                        .filter(line -> line.getEventId() == eventId)
                        .mapToDouble(ReceiptLine::getPriceAtReservation)
                        .sum();

                if (totalRefundForReceipt <= 0) {
                    continue;
                }
                // Extract the payment transaction ID from the receipt's transaction records. This is necessary to tell the payment gateway which transaction we want to refund. If we can't find a valid payment transaction ID, we throw an exception and skip this receipt since we don't want to risk refunding without a valid reference to the original charge.
                int paymentTransactionId = receipt.getPaymentTransactionId()
                        .orElseThrow(() -> new RefundFailedException(
                                receipt.getId(),
                                "receipt does not contain a payment transaction"
                        ));
                // Call the payment gateway to process the refund. This is a critical step that must succeed before we make any changes to our domain state (e.g. marking tickets as refunded or canceling the event) since we want to avoid inconsistencies where we think we've refunded a customer but the payment gateway disagrees.
                RefundResultDTO refundResult = paymentGateway.refund(
                        paymentTransactionId,
                        totalRefundForReceipt
                );
                // Validate the refund result before proceeding. If the refund failed for some reason, we want to throw an exception and avoid making any changes to our domain state (e.g. marking tickets as refunded or canceling the event) since that could lead to inconsistencies.
                validateRefundResult(receipt.getId(), totalRefundForReceipt, refundResult);

                // If we reach this point, the refund was successful and we can update our domain state accordingly.
                receipt.markRefunded(TransactionRecord.refund(
                        refundResult.refundTransactionId(),
                        paymentGateway.getId(),
                        refundResult.totalRefunded(),
                        receipt.getPaymentCurrency(),
                        refundResult.refundedAt()
                ));

                orderReceiptRepository.save(receipt);
            }
            // After processing refunds for all receipts, we need to update the status of all tickets for the event. For simplicity, we assume that if a ticket was PAID or ISSUED, it should be marked as REFUNDED; otherwise, it can be marked as VOIDED. In a real system, we might want to handle this more robustly (e.g. consider different ticket statuses, handle partial refunds, etc.).
            List<Ticket> tickets = ticketRepository.findByEventId(String.valueOf(eventId));
            // Note: we update ticket statuses after processing refunds to avoid complications where we mark tickets as refunded but then encounter an error during the refund process. By only updating ticket statuses after we've successfully processed refunds, we can ensure that our domain state remains consistent with the actual refund status of each order receipt.
            for (Ticket ticket : tickets) {
                if (ticket.getStatus() == TicketStatus.PAID || ticket.getStatus() == TicketStatus.ISSUED) {
                    ticket.markRefunded();
                } else {
                    ticket.markVoided();
                }
                ticketRepository.save(ticket);
            }
            // Finally, we mark the event as canceled. This is a soft cancel that allows us to keep the event in our system for historical and reporting purposes while preventing any future purchases or interactions with it. The event's status will be used in various parts of the system (e.g. purchase flow, event listings) to enforce the fact that it's canceled and should not be available for purchase.
            event.transitionToCanceled("Event canceled by owner");
            eventRepository.save(event);

            log.info("Event {} canceled and refund flow completed", eventId);

        } finally {
            eventRepository.unlock(eventId);
        }
    }



    // helper function for cancelEventAndRefund to validate refund results from the payment gateway and throw domain-specific exceptions if something looks wrong. This keeps the main flow cleaner and centralizes refund validation logic.
    private void validateRefundResult(int receiptId, double expectedRefundAmount, RefundResultDTO refundResult) {
        if (refundResult == null) {
            throw new RefundFailedException(receiptId, "payment gateway returned null refund result");
        }

        if (refundResult.refundTransactionId() == null || refundResult.refundTransactionId().isBlank()) {
            throw new RefundFailedException(receiptId, "refund transaction id is missing");
        }

        if (Math.abs(refundResult.totalRefunded() - expectedRefundAmount) > 0.0001) {
            throw new RefundFailedException(receiptId, "refund amount mismatch");
        }

        if (refundResult.refundedAt() == null) {
            throw new RefundFailedException(receiptId, "refund timestamp is missing");
        }
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
    




    // UC-21 — set / replace event-level purchase + discount policies.
    public void setEventPolicies(String token, EventPolicyConfigDTO config) {
        throw new UnsupportedOperationException("UC-21: not implemented");
    }



    // Detail view for owner-side editing pages.
    public EventDetailDTO getEventDetail(String token, String eventId) {
        throw new UnsupportedOperationException("UC-19: not implemented");
    }
}
