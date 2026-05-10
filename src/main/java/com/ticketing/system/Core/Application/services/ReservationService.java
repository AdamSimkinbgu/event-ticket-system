package com.ticketing.system.Core.Application.services;

import java.time.LocalDateTime;

import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;
import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;
import com.ticketing.system.Core.Domain.events.IEventRepository;

import com.ticketing.system.Core.Domain.events.Event;

public class ReservationService {

    private final IEventRepository eventRepository;
    private final IActiveOrderRepository activeOrderRepository;

    public ReservationService(IEventRepository eventRepository, IActiveOrderRepository activeOrderRepository) {
        this.eventRepository = eventRepository;
        this.activeOrderRepository = activeOrderRepository;
    }


    public boolean reserveTickets(String buyerId, String eventId,String zoneId,int quantity){

        Event event = (Event) eventRepository.findById(eventId);
        double priceoerticket= event.getZone(zoneId).getprice();

            if(!activeOrderRepository.getByUserId(buyerId).equals(null)){

               activeOrderRepository.getByUserId(buyerId).addReservation(eventId,zoneId , quantity, priceoerticket,LocalDateTime.now());
           return true;
            }

            else{

              ActiveOrder newact =new ActiveOrder(buyerId);
              newact.addReservation(eventId,zoneId , quantity, priceoerticket,LocalDateTime.now());
              activeOrderRepository.save(newact);
              return true;
            }
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