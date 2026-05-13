package com.ticketing.system.Core.Application.services;

import java.time.LocalDateTime;

import com.ticketing.system.Core.Application.dto.ReservationResultDTO;
import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;
import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.InventoryZone;

public class ReservationService {

    private final IEventRepository eventRepository;
    private final IActiveOrderRepository activeOrderRepository;

    public ReservationService(IEventRepository eventRepository, IActiveOrderRepository activeOrderRepository) {
        this.eventRepository = eventRepository;
        this.activeOrderRepository = activeOrderRepository;
    }


    public ReservationResultDTO reserveTickets(int buyerId, int eventId, int zoneId, int quantity){

    if (buyerId <= 0) {
        throw new IllegalArgumentException("Invalid buyer id");
    }

   
    if (quantity <= 0) {
        throw new IllegalArgumentException("Quantity must be positive");
    }

    Event event = eventRepository.findById(eventId);
    if (event == null) {
        throw new IllegalArgumentException("Event not found: " + eventId);
    }

    InventoryZone zone = event.getZone(zoneId);
    if (zone == null) {
        throw new IllegalArgumentException("Zone not found: " + zoneId);
    }
double pricePerTicket;
   synchronized (zone) {
    if (zone.getAvailableAmount() < quantity) {
        throw new IllegalArgumentException(
            "Only " + zone.getAvailableAmount() + " tickets left"
        );
    }
     pricePerTicket = zone.getprice();
    zone.reserve(quantity);
      
}

    ActiveOrder activeOrdert = activeOrderRepository.getByUserId(buyerId);

    if (activeOrdert == null) {
        activeOrdert = new ActiveOrder(buyerId);
    }

    activeOrdert.addReservation(eventId, zoneId, quantity, pricePerTicket, LocalDateTime.now());

    activeOrderRepository.save(activeOrdert);

   return new ReservationResultDTO(
        eventId,
        zoneId,
        quantity,
        LocalDateTime.now(),
        pricePerTicket * quantity
        
);
}

            

    // UC-13 — restore a Member's pending ActiveOrder on login (listener of MemberLoggedIn).
    public com.ticketing.system.Core.Application.dto.ActiveOrderDTO restoreActiveOrder(String userId) {
        throw new UnsupportedOperationException("UC-13: not implemented");
    }

    // UC-14 — explicit logout: cancel the order, release locked tickets back to AVAILABLE.
    public void abandonActiveOrder(String userId) {
        throw new UnsupportedOperationException("UC-14: not implemented");
    }

    // UC-2 — scheduled sweep: find and clean up expired ActiveOrders. Cross-aggregate.
    public void expireActiveOrders() {
        throw new UnsupportedOperationException("UC-2: not implemented");
    }

    // UC-5 / UC-9 — read the current cart state (Member or Guest).
    public com.ticketing.system.Core.Application.dto.ActiveOrderDTO viewMyActiveOrder(String userOrSessionId) {
        throw new UnsupportedOperationException("UC-5/9: not implemented");
    }

    // UC-9 — remove a single line from an existing ActiveOrder; releases that ticket lock.
    public void removeFromActiveOrder(String orderId, String ticketId) {
        throw new UnsupportedOperationException("UC-9: not implemented");
    }
}