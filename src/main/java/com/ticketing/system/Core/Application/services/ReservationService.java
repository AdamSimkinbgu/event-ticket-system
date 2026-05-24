package com.ticketing.system.Core.Application.services;

import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ticketing.system.Core.Application.dto.ActiveOrderDTO;
import com.ticketing.system.Core.Application.dto.ReservationResultDTO;
import com.ticketing.system.Core.Application.interfaces.INotificationService;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;
import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.InventoryZone;

@Service
public class ReservationService {

    private final IEventRepository eventRepository;
    private final IActiveOrderRepository activeOrderRepository;
    private final ISessionManager iSessionManager;
    private final INotificationService notificationService;

    private static final Logger eventLogger = LoggerFactory.getLogger("EVENT_LOG");

    public ReservationService(
            IEventRepository eventRepository,
            IActiveOrderRepository activeOrderRepository,
            ISessionManager iSessionManager,
            INotificationService notificationService) {
        this.eventRepository = eventRepository;
        this.activeOrderRepository = activeOrderRepository;
        this.iSessionManager = iSessionManager;
        this.notificationService = notificationService;
    }

    public ReservationResultDTO reserveTicketsForMember(String token, int eventId, int zoneId, int quantity) {
        eventLogger.info("Entered reserveTickets function: eventId={}, zoneId={}, quantity={}",
                eventId, zoneId, quantity);

        InventoryZone reservedZone = null;
        boolean stockReserved = false;

        try {
            int userId = validateTokenAndGetUserId(token);
            validateQuantityForMember(userId, eventId, zoneId, quantity);

            Event event = getEventOrThrowForMember(userId, eventId, zoneId);
            InventoryZone zone = getZoneOrThrowForMember(userId, eventId, zoneId, event);

            ActiveOrder activeOrder = getOrCreateActiveOrderForMember(userId);
            validateNoReservationForEventForMember(activeOrder, userId, eventId, zoneId);

            double pricePerTicket = reserveStock(zone, eventId, zoneId, quantity);

            reservedZone = zone;
            stockReserved = true;

            activeOrder.addReservation(eventId, zoneId, quantity, pricePerTicket, LocalDateTime.now());
            activeOrderRepository.save(activeOrder);

            notificationService.notifyTicketReservationSuccess(userId, eventId, zoneId, quantity);

            return buildReservationResult(eventId, zoneId, quantity);

        } catch (IllegalArgumentException | IllegalStateException e) {
            rollbackReservedStockIfNeeded(stockReserved, reservedZone, quantity, eventId, zoneId);

            eventLogger.warn(
                    "reserveTickets failed: eventId={}, zoneId={}, quantity={}, reason={}",
                    eventId, zoneId, quantity, e.getMessage()
            );

            throw e;
        }
    }

    public ReservationResultDTO reserveTicketsForGuest(String sessionId, int eventId, int zoneId, int quantity) {
        eventLogger.info("Entered reserveTicketsForGuest function: sessionId={}, eventId={}, zoneId={}, quantity={}",
                sessionId, eventId, zoneId, quantity);

        InventoryZone reservedZone = null;
        boolean stockReserved = false;

        try {
            validateSessionId(sessionId);
            validateQuantityForGuest(quantity);

            Event event = getEventOrThrowForGuest(eventId);
            InventoryZone zone = getZoneOrThrowForGuest(event, eventId, zoneId);

            ActiveOrder activeOrder = getOrCreateActiveOrderForGuest(sessionId);
            validateNoReservationForEventForGuest(activeOrder, sessionId, eventId);

            double pricePerTicket = reserveStock(zone, eventId, zoneId, quantity);

            reservedZone = zone;
            stockReserved = true;

            activeOrder.addReservation(eventId, zoneId, quantity, pricePerTicket, LocalDateTime.now());
            activeOrderRepository.save(activeOrder);

            eventLogger.info("Reservation successful for guest session {}", sessionId);

            return buildReservationResult(eventId, zoneId, quantity);

        } catch (IllegalArgumentException | IllegalStateException e) {
            rollbackReservedStockIfNeeded(stockReserved, reservedZone, quantity, eventId, zoneId);

            eventLogger.warn(
                    "reserveTicketsForGuest failed: eventId={}, zoneId={}, quantity={}, reason={}",
                    eventId, zoneId, quantity, e.getMessage()
            );

            throw e;
        }
    }

public ReservationResultDTO removeReservedTickets(String token, int eventId, int zoneId, int quantity) {
    eventLogger.info("Entered removeReservedTickets function: eventId={}, zoneId={}, quantity={}",
            eventId, zoneId, quantity);

    int userId = -1;

    try {
        userId = validateTokenAndGetUserId(token);
        validateRemoveQuantity(quantity);

        Event event = getEventOrThrowForRemove(userId, eventId, zoneId);
        InventoryZone zone = getZoneOrThrowForRemove(userId, event, eventId, zoneId);
        ActiveOrder activeOrder = getActiveOrderOrThrowForRemove(userId, eventId, zoneId);

        removeTicketsFromOrderAndReleaseStock(activeOrder, zone, userId, eventId, zoneId, quantity);

        notificationService.notifyRemoveTicketReservationSuccess(userId, eventId, zoneId, quantity);

        return buildReservationResult(eventId, zoneId, quantity);

    } catch (IllegalArgumentException | IllegalStateException e) {
        notifyRemoveFailureIfPossible(userId, eventId, zoneId, "Remove reservation failed: " + e.getMessage());
        throw e;
    }
}

    private int validateTokenAndGetUserId(String token) {
        if (token == null || token.isBlank()) {
            eventLogger.warn("Request rejected: missing authentication token");
            throw new IllegalArgumentException("Missing authentication token");
        }

        if (!iSessionManager.validateToken(token)) {
            eventLogger.warn("Request rejected: invalid or expired token");
            throw new IllegalStateException("Invalid or expired authentication token");
        }

        int userId = iSessionManager.extractUserId(token);

        if (userId <= 0) {
            eventLogger.warn("Request rejected: invalid buyer id={}", userId);
            throw new IllegalArgumentException("Invalid buyer id");
        }

        return userId;
    }

    private void validateSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            eventLogger.warn("Checkout rejected: missing session ID");
            throw new IllegalArgumentException("Missing session ID");
        }
    }

    private void validateQuantityForMember(int userId, int eventId, int zoneId, int quantity) {
        if (quantity <= 0) {
            notificationService.notifyTicketReservationFailure(
                    userId,
                    eventId,
                    zoneId,
                    "Reservation failed: quantity must be positive"
            );

            eventLogger.warn("reserveTickets rejected: quantity must be positive. quantity={}", quantity);
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    private void validateQuantityForGuest(int quantity) {
        if (quantity <= 0) {
            eventLogger.warn("reserveTicketsForGuest rejected: quantity must be positive. quantity={}", quantity);
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    private Event getEventOrThrowForMember(int userId, int eventId, int zoneId) {
        Event event = eventRepository.findById(eventId);

        if (event == null) {
            notificationService.notifyTicketReservationFailure(
                    userId,
                    eventId,
                    zoneId,
                    "Reservation failed: event not found"
            );

            eventLogger.warn("reserveTickets rejected: event not found. eventId={}", eventId);
            throw new IllegalArgumentException("Event not found: " + eventId);
        }

        return event;
    }

    private Event getEventOrThrowForGuest(int eventId) {
        Event event = eventRepository.findById(eventId);

        if (event == null) {
            eventLogger.warn("reserveTicketsForGuest rejected: event not found. eventId={}", eventId);
            throw new IllegalArgumentException("Event not found: " + eventId);
        }

        return event;
    }

    private Event getEventOrThrowForRemove(int userId, int eventId, int zoneId) {
        Event event = eventRepository.findById(eventId);

        if (event == null) {
            notificationService.notifyRemoveTicketReservationFailure(
                    userId,
                    eventId,
                    zoneId,
                    "Remove reservation failed: event not found"
            );

            eventLogger.warn("removeOneReservedTicket rejected: event not found. eventId={}", eventId);
            throw new IllegalArgumentException("Event not found: " + eventId);
        }

        return event;
    }

    private InventoryZone getZoneOrThrowForMember(int userId, int eventId, int zoneId, Event event) {
        InventoryZone zone = event.getZone(zoneId);

        if (zone == null) {
            notificationService.notifyTicketReservationFailure(
                    userId,
                    eventId,
                    zoneId,
                    "Reservation failed: zone not found"
            );

            eventLogger.warn("reserveTickets rejected: zone not found. eventId={}, zoneId={}",
                    eventId, zoneId);
            throw new IllegalArgumentException("Zone not found: " + zoneId);
        }

        return zone;
    }

    private InventoryZone getZoneOrThrowForGuest(Event event, int eventId, int zoneId) {
        InventoryZone zone = event.getZone(zoneId);

        if (zone == null) {
            eventLogger.warn("reserveTicketsForGuest rejected: zone not found. eventId={}, zoneId={}",
                    eventId, zoneId);
            throw new IllegalArgumentException("Zone not found: " + zoneId);
        }

        return zone;
    }

    private InventoryZone getZoneOrThrowForRemove(int userId, Event event, int eventId, int zoneId) {
        InventoryZone zone = event.getZone(zoneId);

        if (zone == null) {
            notificationService.notifyRemoveTicketReservationFailure(
                    userId,
                    eventId,
                    zoneId,
                    "Remove reservation failed: zone not found"
            );

            eventLogger.warn("removeOneReservedTicket rejected: zone not found. eventId={}, zoneId={}",
                    eventId, zoneId);
            throw new IllegalArgumentException("Zone not found: " + zoneId);
        }

        return zone;
    }

    private ActiveOrder getOrCreateActiveOrderForMember(int userId) {
        ActiveOrder activeOrder = activeOrderRepository.getByUserId(userId);

        if (activeOrder == null) {
            eventLogger.info("No active order found for userId={}, creating new ActiveOrder", userId);
            activeOrder = new ActiveOrder(userId);
        }

        return activeOrder;
    }

    private ActiveOrder getOrCreateActiveOrderForGuest(String sessionId) {
        Optional<ActiveOrder> existingOrderOpt = activeOrderRepository.getBySessionId(sessionId);

        if (existingOrderOpt.isPresent()) {
            return existingOrderOpt.get();
        }

        eventLogger.info("No active order found for sessionId={}, creating new ActiveOrder", sessionId);
        return ActiveOrder.forGuest(sessionId);
    }

    private ActiveOrder getActiveOrderOrThrowForRemove(int userId, int eventId, int zoneId) {
        ActiveOrder activeOrder = activeOrderRepository.getByUserId(userId);

        if (activeOrder == null) {
            notificationService.notifyRemoveTicketReservationFailure(
                    userId,
                    eventId,
                    zoneId,
                    "Remove reservation failed: active order not found"
            );

            eventLogger.warn("removeOneReservedTicket rejected: active order not found. userId={}", userId);
            throw new IllegalArgumentException("Active order not found");
        }

        return activeOrder;
    }

    private void validateNoReservationForEventForMember(
            ActiveOrder activeOrder,
            int userId,
            int eventId,
            int zoneId) {
        if (activeOrder.hasReservationForEvent(eventId)) {
            notificationService.notifyTicketReservationFailure(
                    userId,
                    eventId,
                    zoneId,
                    "Reservation failed: user already has an active order for this event"
            );

            throw new IllegalStateException("User already has an active order for this event");
        }
    }

    private void validateNoReservationForEventForGuest(
            ActiveOrder activeOrder,
            String sessionId,
            int eventId) {
        if (activeOrder.hasReservationForEvent(eventId)) {
            eventLogger.warn("Reservation failed: guest already has an active order for this event. sessionId={}",
                    sessionId);
            throw new IllegalStateException("User already has an active order for this event");
        }
    }

    private double reserveStock(InventoryZone zone, int eventId, int zoneId, int quantity) {
    double pricePerTicket = zone.getprice();

    zone.reserve(quantity);

    eventLogger.info(
            "Tickets reserved in zone stock: eventId={}, zoneId={}, quantity={}, pricePerTicket={}",
            eventId, zoneId, quantity, pricePerTicket
    );

    return pricePerTicket;
}

   private void rollbackReservedStockIfNeeded(
        boolean stockReserved,
        InventoryZone reservedZone,
        int quantity,
        int eventId,
        int zoneId) {

    if (stockReserved && reservedZone != null) {
        reservedZone.release(quantity);

        eventLogger.warn("Rollback completed: released {} tickets for eventId={}, zoneId={}",
                quantity, eventId, zoneId);
    }
}

    private void notifyRemoveFailureIfPossible(int userId, int eventId, int zoneId, String message) {
        if (userId > 0) {
            notificationService.notifyRemoveTicketReservationFailure(
                    userId,
                    eventId,
                    zoneId,
                    message
            );
        }
    }

    private void removeTicketsFromOrderAndReleaseStock(  ActiveOrder activeOrder, InventoryZone zone, int userId, int eventId, int zoneId, int quantity) {

    activeOrder.removeTickets(eventId, zoneId, quantity);
    zone.release(quantity);
    activeOrderRepository.save(activeOrder);
}

    private ReservationResultDTO buildReservationResult(int eventId, int zoneId, int quantity) {
        return new ReservationResultDTO(
                eventId,
                zoneId,
                quantity,
                LocalDateTime.now()
        );
    }

private void validateRemoveQuantity(int quantity) {
    if (quantity <= 0) {
        throw new IllegalArgumentException("Quantity must be positive");
    }
}


    public com.ticketing.system.Core.Application.dto.ActiveOrderDTO restoreActiveOrder(int userId) {
 ActiveOrder activeOrder = activeOrderRepository.getByUserId(userId);
        if (activeOrder == null) {
            eventLogger.info("No active order found for userId={}, returning null", userId);
            return null;
        }
        eventLogger.info("Active order found for userId={}, restoring ActiveOrderDTO", userId);
        ActiveOrderDTO activeOrderDTO = activeOrder.toDTO();
        for (ActiveOrderDTO.CartLineDTO line : activeOrderDTO.lines()) {
            String eventName = eventRepository.findById(line.eventId()).getName();
            line = new ActiveOrderDTO.CartLineDTO(
                    line.eventId(),
                    eventName,
                    line.zoneId(),
                    line.seatNumber(),
                    line.pricePerTicket(),
                    line.addedAt());
        }
        return activeOrderDTO;   
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

   
}