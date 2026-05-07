package com.ticketing.system.Core.Application.services;

import java.time.LocalDateTime;

import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;
import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;
import com.ticketing.system.Core.Domain.events.EventRepository;

import com.ticketing.system.Core.Domain.events.Event;

public class ReservationService {

    private final EventRepository eventRepository;
    private final IActiveOrderRepository activeOrderRepository;

    public ReservationService(EventRepository eventRepository, IActiveOrderRepository activeOrderRepository) {
        this.eventRepository = eventRepository;
        this.activeOrderRepository = activeOrderRepository;
    }

                               
    public void reserveTickets(String buyerId, String eventId,String zoneId,int quantity){

        Event event = (Event) eventRepository.findById(eventId);
        double priceoerticket= event.getZone(zoneId).getprice();

            if(!activeOrderRepository.getByUserId(buyerId).equals(null)){

               activeOrderRepository.getByUserId(buyerId).addReservation(eventId,zoneId , quantity, priceoerticket,LocalDateTime.now()); 
            } 
            else{

              ActiveOrder newact =new ActiveOrder(buyerId);
              newact.addReservation(eventId,zoneId , quantity, priceoerticket,LocalDateTime.now());
              activeOrderRepository.save(newact);
            }

           
        }
}