package com.ticketing.system.Core.Application.services;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.system.Core.Application.dto.ReservationResultDTO;
import com.ticketing.system.Core.Application.interfaces.INotificationService;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;
import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.InventoryZone;

public class ReservationService {

    private final IEventRepository eventRepository;
    private final IActiveOrderRepository activeOrderRepository;
      private final ISessionManager iSessionManager;
    private static final Logger eventLogger = LoggerFactory.getLogger("EVENT_LOG");
    private final INotificationService notificationService;

    public ReservationService(IEventRepository eventRepository, IActiveOrderRepository activeOrderRepository,  ISessionManager iSessionManager, INotificationService notificationService) {
        this.eventRepository = eventRepository;
        this.activeOrderRepository = activeOrderRepository;
        this.iSessionManager = iSessionManager;
        this.notificationService = notificationService;
    }

    public ReservationResultDTO reserveTickets(String token, int eventId, int zoneId, int quantity) {
        eventLogger.info("Entered reserveTickets function: eventId={}, zoneId={}, quantity={}",
                eventId, zoneId, quantity);

         InventoryZone reservedZone = null;
        boolean stockReserved = false;
        try {
            if (token == null || token.isBlank()) {
                eventLogger.warn("Checkout rejected: missing authentication token");
                throw new IllegalArgumentException("Missing authentication token");
            }

            if (!iSessionManager.validateToken(token)) {
                eventLogger.warn("Checkout rejected: invalid or expired token");
                throw new IllegalStateException("Invalid or expired authentication token");
            }

            int userId = iSessionManager.extractUserId(token);

            if (userId <= 0) {
                eventLogger.warn("reserveTickets rejected: invalid buyer id={}", userId);
                throw new IllegalArgumentException("Invalid buyer id");
            }

            if (quantity <= 0) {
                notificationService.notifyTicketReservationFailure(userId, eventId, zoneId, "Reservation failed: quantity must be positive");
                eventLogger.warn("reserveTickets rejected: quantity must be positive. quantity={}", quantity);
                throw new IllegalArgumentException("Quantity must be positive");
            }

            Event event = eventRepository.findById(eventId);
            if (event == null) {
                notificationService.notifyTicketReservationFailure( userId,  eventId,  zoneId, "Reservation failed: event not found");
                eventLogger.warn("reserveTickets rejected: event not found. eventId={}", eventId);
                throw new IllegalArgumentException("Event not found: " + eventId);
            }

            InventoryZone zone = event.getZone(zoneId);
            if (zone == null) {
                notificationService.notifyTicketReservationFailure(userId,eventId, zoneId, "Reservation failed: zone not found");
                eventLogger.warn("reserveTickets rejected: zone not found. eventId={}, zoneId={}",
                        eventId, zoneId);
                throw new IllegalArgumentException("Zone not found: " + zoneId);
            }

            double pricePerTicket;
             ActiveOrder activeOrdert = activeOrderRepository.getByUserId(userId);

            if (activeOrdert == null) {
                eventLogger.info("No active order found for userId={}, creating new ActiveOrder", userId);
                activeOrdert = new ActiveOrder(userId);
            }
if (activeOrdert.hasReservationForEvent(eventId)) {
    notificationService.notifyTicketReservationFailure(
            userId,
            eventId,
            zoneId,
            "Reservation failed: user already has an active order for this event"
    );

    throw new IllegalStateException("User already has an active order for this event");
}

            synchronized (zone) {
                if (zone.getAvailableAmount() < quantity) {
                    notificationService.notifyTicketReservationFailure(
                            userId,
                            eventId, zoneId,
                            "Reservation failed: only " + zone.getAvailableAmount() + " tickets left"
                    );

                    eventLogger.warn(
                            "reserveTickets rejected: not enough tickets. eventId={}, zoneId={}, requested={}, available={}",
                            eventId, zoneId, quantity, zone.getAvailableAmount()
                    );

                    throw new IllegalArgumentException(
                            "Only " + zone.getAvailableAmount() + " tickets left"
                    );
                }

                pricePerTicket = zone.getprice();
                zone.reserve(quantity);

                eventLogger.info(
                        "Tickets reserved in zone stock: eventId={}, zoneId={}, quantity={}, pricePerTicket={}",
                        eventId, zoneId, quantity, pricePerTicket
                );
            }
             reservedZone = zone;
            stockReserved = true;

           

            activeOrdert.addReservation(eventId, zoneId, quantity, pricePerTicket, LocalDateTime.now());

            activeOrderRepository.save(activeOrdert);

            notificationService.notifyTicketReservationSuccess(
                    userId, eventId, zoneId,quantity
            );

            return new ReservationResultDTO(
                    eventId,
                    zoneId,
                    quantity,
                    LocalDateTime.now()
            );

        } catch (IllegalArgumentException | IllegalStateException e) {
            if (stockReserved && reservedZone != null) {
                synchronized (reservedZone) {
                    reservedZone.release(quantity);
                }

                eventLogger.warn("Rollback completed: released {} tickets for eventId={}, zoneId={}",
                        quantity, eventId, zoneId);
            }

            eventLogger.warn(
                    "reserveTickets failed: eventId={}, zoneId={}, quantity={}, reason={}",
                    eventId, zoneId, quantity, e.getMessage()
            );

            throw e;

        } 

    }


    public ReservationResultDTO removeOneReservedTicket(String token, int eventId, int zoneId) {
    eventLogger.info("Entered removeOneReservedTicket function: eventId={}, zoneId={}",
            eventId, zoneId);

    try {
        if (token == null || token.isBlank()) {
            eventLogger.warn("removeOneReservedTicket rejected: missing authentication token");
            throw new IllegalArgumentException("Missing authentication token");
        }

        if (!iSessionManager.validateToken(token)) {
            eventLogger.warn("removeOneReservedTicket rejected: invalid or expired token");
            throw new IllegalStateException("Invalid or expired authentication token");
        }

        int userId = iSessionManager.extractUserId(token);

        if (userId <= 0) {
            eventLogger.warn("removeOneReservedTicket rejected: invalid buyer id={}", userId);
            throw new IllegalArgumentException("Invalid buyer id");
        }

        Event event = eventRepository.findById(eventId);
        if (event == null) {
            notificationService.notifyRemoveTicketReservationFailure(userId, eventId, zoneId,
                    "Remove reservation failed: event not found");
            eventLogger.warn("removeOneReservedTicket rejected: event not found. eventId={}", eventId);
            throw new IllegalArgumentException("Event not found: " + eventId);
        }

        InventoryZone zone = event.getZone(zoneId);
        if (zone == null) {
            notificationService.notifyRemoveTicketReservationFailure(userId, eventId, zoneId,
                    "Remove reservation failed: zone not found");
            eventLogger.warn("removeOneReservedTicket rejected: zone not found. eventId={}, zoneId={}",
                    eventId, zoneId);
            throw new IllegalArgumentException("Zone not found: " + zoneId);
        }

        ActiveOrder activeOrder = activeOrderRepository.getByUserId(userId);

        if (activeOrder == null) {
            notificationService.notifyRemoveTicketReservationFailure(userId, eventId, zoneId,
                    "Remove reservation failed: active order not found");
            eventLogger.warn("removeOneReservedTicket rejected: active order not found. userId={}", userId);
            throw new IllegalArgumentException("Active order not found");
        }

        activeOrder.removeOneTicket(eventId, zoneId);

        synchronized (zone) {
            zone.release(1);
        }

        activeOrderRepository.save(activeOrder);

        notificationService.notifyRemoveTicketReservationSuccess(
                userId,
                eventId,
                zoneId,
                1
        );

        eventLogger.info("removeOneReservedTicket completed successfully: userId={}, eventId={}, zoneId={}",
                userId, eventId, zoneId);

        return new ReservationResultDTO(
                eventId,
                zoneId,
                1,
                LocalDateTime.now()
        );

    } catch (IllegalArgumentException | IllegalStateException e) {
        eventLogger.warn(
                "removeOneReservedTicket failed: eventId={}, zoneId={}, reason={}",
                eventId, zoneId, e.getMessage()
        );
        throw e;

    } catch (Exception e) {
        eventLogger.error(
                "Unexpected error in removeOneReservedTicket: eventId={}, zoneId={}",
                eventId, zoneId, e
        );
        throw new RuntimeException("Failed to remove reserved ticket", e);
    }
}

    public com.ticketing.system.Core.Application.dto.ActiveOrderDTO restoreActiveOrder(String userId) {
        throw new UnsupportedOperationException("UC-13: not implemented");
    }

    public void abandonActiveOrder(String userId) {
        throw new UnsupportedOperationException("UC-14: not implemented");
    }

    public void expireActiveOrders() {
        throw new UnsupportedOperationException("UC-2: not implemented");
    }

    public com.ticketing.system.Core.Application.dto.ActiveOrderDTO viewMyActiveOrder(String userOrSessionId) {
        throw new UnsupportedOperationException("UC-5/9: not implemented");
    }

    public void removeFromActiveOrder(String orderId, String ticketId) {
        throw new UnsupportedOperationException("UC-9: not implemented");
    }
}