package com.ticketing.system.Core.Application.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.ticketing.system.Core.Application.dto.ActiveOrderDTO;
import com.ticketing.system.Core.Application.dto.ReservationResultDTO;
import com.ticketing.system.Core.Application.interfaces.INotificationService;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;
import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.InventorySelection;
import com.ticketing.system.Core.Domain.events.InventoryZone;

@Service
@Slf4j
public class ReservationService {

    private final IEventRepository eventRepository;
    private final IActiveOrderRepository activeOrderRepository;
    private final ISessionManager iSessionManager;
    private final INotificationService notificationService;

    private final int reservationTimeoutMinutes = 10;  // TODO: get this from config or constant

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






    public ReservationResultDTO reserveStandingTicketsForMember(String token, int eventId, int zoneId, int quantity) {
        log.info("Entered reserveStandingTicketsForMember function: eventId={}, zoneId={}, quantity={}",
                eventId, zoneId, quantity);

        boolean stockReserved = false;
        ActiveOrder activeOrder = null;

        try {
            int userId = validateTokenAndGetUserId(token);
            validateQuantityForMember(userId, eventId, zoneId, quantity);

            Event event = getEventOrThrowForMember(userId, eventId, zoneId);
            InventoryZone zone = getZoneOrThrowForMember(userId, eventId, zoneId, event);
            activeOrder = getOrCreateActiveOrderForMember(userId);
            // validateNoReservationForEventForMember(activeOrder, userId, eventId, zoneId);  // he can buy more from the same event he already bought from

            double pricePerTicket = reserveStandingStock(event, zone, zoneId, quantity);
            stockReserved = true;

            activeOrder.addStandingReservation(eventId, zoneId, quantity, pricePerTicket, LocalDateTime.now());
            activeOrderRepository.save(activeOrder);

            notificationService.notifyTicketReservationSuccess(userId, eventId, zoneId, quantity);

            return buildReservationResult(eventId, zoneId, quantity, List.of());

        } catch (IllegalArgumentException | IllegalStateException e) {

            if (stockReserved) {
                try {
                    activeOrder.removeStandingSpots(eventId, zoneId, quantity);
                } catch (Exception ignored) {
                    // best-effort rollback
                }
                rollbackReservedStandingStockIfNeeded(quantity, eventId, zoneId);
            }

            log.warn(
                    "reserveTickets failed: eventId={}, zoneId={}, quantity={}, reason={}",
                    eventId, zoneId, quantity, e.getMessage());

            throw e;
        }
    }

    
    //?NOTE: I thought about merging these above and below functions but I saw that more and more of the code is diverging between member and guest flows,
    //?      so I think it's better to keep them separate for now. We can always refactor later if we see a lot of duplication.


    public ReservationResultDTO reserveStandingTicketsForGuest(String sessionId, int eventId, int zoneId, int quantity) {
        log.info("Entered reserveStandingTicketsForGuest function: sessionId={}, eventId={}, zoneId={}, quantity={}",
                sessionId, eventId, zoneId, quantity);

        boolean stockReserved = false;
        ActiveOrder activeOrder = null;

        try {
            validateSessionId(sessionId);
            validateQuantityForGuest(quantity);

            Event event = getEventOrThrowForGuest(eventId);
            InventoryZone zone = getZoneOrThrowForGuest(event, eventId, zoneId);

            activeOrder = getOrCreateActiveOrderForGuest(sessionId);

            // validateNoReservationForEventForGuest(activeOrder, sessionId, eventId);  // he can buy more from the same event he already bought from

            double pricePerTicket = reserveStandingStock(event, zone, zoneId, quantity);
            stockReserved = true;

            activeOrder.addStandingReservation(eventId, zoneId, quantity, pricePerTicket, LocalDateTime.now());
            activeOrderRepository.save(activeOrder);

            // he's a guest so no notification for him
            // notificationService.notifyTicketReservationSuccess(Integer.parseInt(sessionId), eventId, zoneId, quantity);
            log.info("Reservation successful for guest session {}", sessionId);

            return buildReservationResult(eventId, zoneId, quantity, List.of());

        } catch (IllegalArgumentException | IllegalStateException e) {
            if (stockReserved) {
                try {
                    activeOrder.removeStandingSpots(eventId, zoneId, quantity);
                } catch (Exception ignored) {
                    // best-effort rollback
                }
                rollbackReservedStandingStockIfNeeded(quantity, eventId, zoneId);
            }

            log.warn(
                    "reserveTicketsForGuest failed: eventId={}, zoneId={}, quantity={}, reason={}",
                    eventId, zoneId, quantity, e.getMessage());

            throw e;
        }
    }






    // The seat flow should be:
    // authenticate / validate session
    // load event
    // load zone
    // validate zone is seated
    // load/create active order
    // validate no conflicting reservation
    // event.reserveSeats(zoneId, seatNumbers)
    // activeOrder.addSeatedReservation(...)
    // save event
    // save active order
    // return ReservationResultDTO with seatNumbers

    public ReservationResultDTO reserveSeatsForMember(String token, int eventId, int zoneId, List<String> seatNumbers) {
        int userId = validateTokenAndGetUserId(token);

        Event event = getEventOrThrowForMember(userId, eventId, zoneId);
        InventoryZone zone = getZoneOrThrowForMember(userId, eventId, zoneId, event);

        if (!zone.isSeated()) {
            throw new IllegalArgumentException("Zone is not a seated zone");
        }

        ActiveOrder activeOrder = getOrCreateActiveOrderForMember(userId);
        // validateNoReservationForEventForMember(activeOrder, userId, eventId, zoneId);  // he can buy more from the same event he already bought from

        boolean stockReserved = false;

        try {
            event.reserveSeats(zoneId, seatNumbers);
            stockReserved = true;

            activeOrder.addSeatedReservation(eventId, zoneId, seatNumbers, zone.getprice(), LocalDateTime.now());

            eventRepository.save(event);
            activeOrderRepository.save(activeOrder);

            notificationService.notifyTicketReservationSuccess(userId, eventId, zoneId, seatNumbers.size());

            return buildReservationResult(eventId, zoneId, seatNumbers.size(), seatNumbers);

        } catch (RuntimeException e) {
            if (stockReserved) {
                try {
                    activeOrder.removeSeats(eventId, zoneId, seatNumbers);
                } catch (Exception ignored) {
                    // best-effort rollback
                }

                event.releaseSeats(zoneId, seatNumbers);
                eventRepository.save(event);
            }
            log.warn("reserveSeatsForMember failed: eventId={}, zoneId={}, seatNumbers={}, reason={}", eventId, zoneId,
                    seatNumbers, e.getMessage());
            throw e;
        }
    }



    public ReservationResultDTO reserveSeatsForGuest(String sessionId, int eventId, int zoneId,
            List<String> seatNumbers) {
        validateSessionId(sessionId);

        Event event = getEventOrThrowForGuest(eventId);
        InventoryZone zone = getZoneOrThrowForGuest(event, eventId, zoneId);

        if (!zone.isSeated()) {
            throw new IllegalArgumentException("Zone is not a seated zone");
        }

        ActiveOrder activeOrder = getOrCreateActiveOrderForGuest(sessionId);
        // validateNoReservationForEventForGuest(activeOrder, sessionId, eventId);  // he can buy more from the same event he already bought from

        boolean stockReserved = false;

        try {
            event.reserveSeats(zoneId, seatNumbers);
            stockReserved = true;

            activeOrder.addSeatedReservation(eventId, zoneId, seatNumbers, zone.getprice(), LocalDateTime.now());

            eventRepository.save(event);
            activeOrderRepository.save(activeOrder);

            // he's a guest so no notification for him
            // notificationService.notifyTicketReservationSuccess(Integer.parseInt(sessionId), eventId, zoneId, seatNumbers.size());

            return buildReservationResult(eventId, zoneId, seatNumbers.size(), seatNumbers);

        } catch (RuntimeException e) {
            if (stockReserved) {
                try {
                    activeOrder.removeSeats(eventId, zoneId, seatNumbers);
                } catch (Exception ignored) {
                    // best-effort rollback
                }

                event.releaseSeats(zoneId, seatNumbers);
                eventRepository.save(event);
            }
            log.warn("reserveSeatsForGuest failed: eventId={}, zoneId={}, seatNumbers={}, reason={}", eventId, zoneId,
                    seatNumbers, e.getMessage());
            throw e;
        }
    }













    
    public ReservationResultDTO removeReservedStandingSpotsForMember(String token, int eventId, int zoneId, int quantity) {
        log.info("Entered removeReservedStandingSpotsForMember function: eventId={}, zoneId={}, quantity={}", eventId,
                zoneId,
                quantity);

        int userId = -1;

        try {
            userId = validateTokenAndGetUserId(token);
            validateRemoveQuantity(quantity);

            Event event = getEventOrThrowForRemove(userId, eventId, zoneId);
            InventoryZone zone = getZoneOrThrowForRemove(userId, event, eventId, zoneId);
            ActiveOrder activeOrder = getActiveOrderOrThrowForRemove(userId, eventId, zoneId);

            removeStandingTicketsFromOrderAndReleaseStock(activeOrder, zone, userId, eventId, zoneId, quantity);

            notificationService.notifyRemoveTicketReservationSuccess(userId, eventId, zoneId, quantity);

            return buildReservationResult(eventId, zoneId, quantity, List.of());

        } catch (IllegalArgumentException | IllegalStateException e) {
            notifyRemoveFailureIfPossible(userId, eventId, zoneId,
                    "Remove reservation for member failed: " + e.getMessage());
            throw e;
        }
    }
    


    public ReservationResultDTO removeReservedStandingSpotsForGuest(String sessionId, int eventId, int zoneId, int quantity) {
        log.info("Entered removeReservedStandingSpotsForGuest function: sessionId={}, eventId={}, zoneId={}, quantity={}",
                sessionId, eventId, zoneId, quantity);

        try {
            validateSessionId(sessionId);
            validateRemoveQuantity(quantity);

            Event event = getEventOrThrowForGuest(eventId);
            InventoryZone zone = getZoneOrThrowForGuest(event, eventId, zoneId);

            ActiveOrder activeOrder = activeOrderRepository.getBySessionId(sessionId)
                    .orElseThrow(() -> {
                        log.warn("removeReservedStandingSpotsForGuest rejected: active order not found. sessionId={}", sessionId);
                        return new IllegalArgumentException("Active order not found");
                    });

            removeStandingTicketsFromOrderAndReleaseStock(activeOrder, zone, 0, eventId, zoneId, quantity);

            return buildReservationResult(eventId, zoneId, quantity, List.of());

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("removeReservedStandingSpotsForGuest failed: eventId={}, zoneId={}, quantity={}, reason={}",
                    eventId, zoneId, quantity, e.getMessage());
            throw e;
        }
    }
    









    // flow:
    // load user
    // load event
    // load active order
    // activeOrder.removeSeats(eventId, zoneId, seatNumbers)
    // event.releaseSeats(zoneId, seatNumbers)
    // save active order
    // save event
    public ReservationResultDTO removeReservedSeatsForMember(String token, int eventId, int zoneId, List<String> seatNumbers) {
        log.info("Entered removeReservedSeatsForMember function: eventId={}, zoneId={}, seatNumbers={}", eventId, zoneId, seatNumbers);

        int userId = -1;

        try {
            userId = validateTokenAndGetUserId(token);

            Event event = getEventOrThrowForRemove(userId, eventId, zoneId);
            ActiveOrder activeOrder = getActiveOrderOrThrowForRemove(userId, eventId, zoneId);

            activeOrder.validateContainsSeats(eventId, zoneId, seatNumbers);

            event.releaseSeats(zoneId, seatNumbers);

            activeOrder.removeSeats(eventId, zoneId, seatNumbers);

            eventRepository.save(event);
            activeOrderRepository.save(activeOrder);

            notificationService.notifyRemoveTicketReservationSuccess(userId, eventId, zoneId, seatNumbers.size());

            return buildReservationResult(eventId, zoneId, seatNumbers.size(), seatNumbers);

        } catch (Exception e) {
            log.warn("removeReservedSeats failed: eventId={}, zoneId={}, seatNumbers={}, reason={}", eventId, zoneId,
                    seatNumbers, e.getMessage());
            notificationService.notifyRemoveTicketReservationFailure(userId, eventId, zoneId,
                    "Remove reservation for member failed: " + e.getMessage());
            throw e;
        }
    }

    


    public ReservationResultDTO removeReservedSeatsForGuest(String sessionId, int eventId, int zoneId, List<String> seatNumbers) {
        log.info("Entered removeReservedSeatsForGuest function: sessionId={}, eventId={}, zoneId={}, seatNumbers={}",
                sessionId, eventId, zoneId, seatNumbers);

        try {
            validateSessionId(sessionId);

            Event event = getEventOrThrowForGuest(eventId);
            InventoryZone zone = getZoneOrThrowForGuest(event, eventId, zoneId);

            if (!zone.isSeated()) {
                throw new IllegalArgumentException("Zone is not a seated zone");
            }

            ActiveOrder activeOrder = activeOrderRepository.getBySessionId(sessionId)
                    .orElseThrow(() -> {
                        log.warn("removeReservedSeatsForGuest rejected: active order not found. sessionId={}", sessionId);
                        return new IllegalArgumentException("Active order not found");
                    });

            activeOrder.validateContainsSeats(eventId, zoneId, seatNumbers);

            event.releaseSeats(zoneId, seatNumbers);

            activeOrder.removeSeats(eventId, zoneId, seatNumbers);

            eventRepository.save(event);
            activeOrderRepository.save(activeOrder);

            return buildReservationResult(eventId, zoneId, seatNumbers.size(), seatNumbers);

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("removeReservedSeatsForGuest failed: eventId={}, zoneId={}, seatNumbers={}, reason={}",
                    eventId, zoneId, seatNumbers, e.getMessage());
            throw e;
        }
    }





















    private int validateTokenAndGetUserId(String token) {
        if (token == null || token.isBlank()) {
            log.warn("Request rejected: missing authentication token");
            throw new IllegalArgumentException("Missing authentication token");
        }

        if (!iSessionManager.validateToken(token)) {
            log.warn("Request rejected: invalid or expired token");
            throw new IllegalStateException("Invalid or expired authentication token");
        }

        int userId = iSessionManager.extractUserId(token);

        if (userId <= 0) {
            log.warn("Request rejected: invalid buyer id={}", userId);
            throw new IllegalArgumentException("Invalid buyer id");
        }

        return userId;
    }

    private void validateSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("Checkout rejected: missing session ID");
            throw new IllegalArgumentException("Missing session ID");
        }
    }






    private void validateQuantityForMember(int userId, int eventId, int zoneId, int quantity) {
        if (quantity <= 0) {
            notificationService.notifyTicketReservationFailure(
                    userId,
                    eventId,
                    zoneId,
                    "Reservation failed: quantity must be positive");

            log.warn("reserveTickets rejected: quantity must be positive. quantity={}", quantity);
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    private void validateQuantityForGuest(int quantity) {
        if (quantity <= 0) {
            log.warn("reserveTicketsForGuest rejected: quantity must be positive. quantity={}", quantity);
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    private Event getEventOrThrowForMember(int userId, int eventId, int zoneId) {
        Event event = eventRepository.findById(eventId);

        if (event == null) {
            notificationService.notifyTicketReservationFailure(userId, eventId, zoneId,
                    "Reservation failed: event not found");

            log.warn("reserveTickets rejected: event not found. eventId={}", eventId);
            throw new IllegalArgumentException("Event not found: " + eventId);
        }

        return event;
    }

    private Event getEventOrThrowForGuest(int eventId) {
        Event event = eventRepository.findById(eventId);

        if (event == null) {
            log.warn("reserveTicketsForGuest rejected: event not found. eventId={}", eventId);
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
                    "Remove reservation failed: event not found");

            log.warn("removeOneReservedTicket rejected: event not found. eventId={}", eventId);
            throw new IllegalArgumentException("Event not found: " + eventId);
        }

        return event;
    }

    private InventoryZone getZoneOrThrowForMember(int userId, int eventId, int zoneId, Event event) {
        InventoryZone zone = null;
        if (event.getVenueMap() != null) {
            zone = event.getVenueMap().getZone(zoneId);   
        }

        if (zone == null) {
            notificationService.notifyTicketReservationFailure(
                    userId,
                    eventId,
                    zoneId,
                    "Reservation failed: zone not found");

            log.warn("reserveTickets rejected: zone not found. eventId={}, zoneId={}",
                    eventId, zoneId);
            throw new IllegalArgumentException("Zone not found: " + zoneId);
        }

        return zone;
    }

    private InventoryZone getZoneOrThrowForGuest(Event event, int eventId, int zoneId) {
        InventoryZone zone = null;
        if (event.getVenueMap() != null) {
            zone = event.getVenueMap().getZone(zoneId);
        }

        if (zone == null) {
            log.warn("reserveTicketsForGuest rejected: zone not found. eventId={}, zoneId={}",
                    eventId, zoneId);
            throw new IllegalArgumentException("Zone not found: " + zoneId);
        }

        return zone;
    }

    private InventoryZone getZoneOrThrowForRemove(int userId, Event event, int eventId, int zoneId) {
        InventoryZone zone = null;
        if (event.getVenueMap() != null) {
            zone = event.getVenueMap().getZone(zoneId);
        }

        if (zone == null) {
            notificationService.notifyRemoveTicketReservationFailure(
                    userId,
                    eventId,
                    zoneId,
                    "Remove reservation failed: zone not found");

            log.warn("removeOneReservedTicket rejected: zone not found. eventId={}, zoneId={}",
                    eventId, zoneId);
            throw new IllegalArgumentException("Zone not found: " + zoneId);
        }

        return zone;
    }

    private ActiveOrder getOrCreateActiveOrderForMember(int userId) {
        ActiveOrder activeOrder = activeOrderRepository.getByUserId(userId);

        if (activeOrder == null) {
            log.info("No active order found for userId={}, creating new ActiveOrder", userId);
            activeOrder = new ActiveOrder(userId);
        }

        return activeOrder;
    }

    private ActiveOrder getOrCreateActiveOrderForGuest(String sessionId) {
        Optional<ActiveOrder> existingOrderOpt = activeOrderRepository.getBySessionId(sessionId);

        if (existingOrderOpt.isPresent()) {
            return existingOrderOpt.get();
        }

        log.info("No active order found for sessionId={}, creating new ActiveOrder", sessionId);
        return ActiveOrder.forGuest(sessionId);
    }

    private ActiveOrder getActiveOrderOrThrowForRemove(int userId, int eventId, int zoneId) {
        ActiveOrder activeOrder = activeOrderRepository.getByUserId(userId);

        if (activeOrder == null) {
            notificationService.notifyRemoveTicketReservationFailure(
                    userId,
                    eventId,
                    zoneId,
                    "Remove reservation failed: active order not found");

            log.warn("removeOneReservedTicket rejected: active order not found. userId={}", userId);
            throw new IllegalArgumentException("Active order not found");
        }

        return activeOrder;
    }

    // NOTE: he can buy more from the same event he already bought from
    // private void validateNoReservationForEventForMember(ActiveOrder activeOrder, int userId, int eventId, int zoneId) {
    //     if (activeOrder.hasReservationForEvent(eventId)) {
    //         notificationService.notifyTicketReservationFailure(
    //                 userId,
    //                 eventId,
    //                 zoneId,
    //                 "Reservation failed: user already has an active order for this event"
    //         );

    //         throw new IllegalStateException("User already has an active order for this event");
    //     }
    // }

    // private void validateNoReservationForEventForGuest(ActiveOrder activeOrder, String sessionId, int eventId) {
    //     if (activeOrder.hasReservationForEvent(eventId)) {
    //         log.warn("Reservation failed: guest already has an active order for this event. sessionId={}",
    //                 sessionId);
    //         throw new IllegalStateException("User already has an active order for this event");
    //     }
    // }
    // NOTE: he can buy more from the same event he already bought from
       

    private double reserveStandingStock(Event event, InventoryZone zone, int zoneId, int quantity) {
        double pricePerTicket = zone.getprice();

        event.reserveStandingSpots(zoneId, quantity);
        eventRepository.save(event);

        return pricePerTicket;
    }

    private void rollbackReservedStandingStockIfNeeded(int quantity, int eventId, int zoneId) {
        log.warn("Rolling back reserved stock: releasing {} tickets for eventId={}, zoneId={}", quantity, eventId,
                zoneId);
        eventRepository.findById(eventId).releaseStandingSpots(zoneId, quantity);
        eventRepository.save(eventRepository.findById(eventId));
        log.warn("Rollback completed: released {} tickets for eventId={}, zoneId={}", quantity, eventId, zoneId);
    }

    private void notifyRemoveFailureIfPossible(int userId, int eventId, int zoneId, String message) {
        if (userId > 0) {
            notificationService.notifyRemoveTicketReservationFailure(userId, eventId, zoneId, message);
        }
    }

    private void removeStandingTicketsFromOrderAndReleaseStock(ActiveOrder activeOrder, InventoryZone zone, int userId,
            int eventId, int zoneId, int quantity) {
        activeOrder.removeStandingSpots(eventId, zoneId, quantity);
        this.eventRepository.findById(eventId).releaseStandingSpots(zoneId, quantity);
        this.eventRepository.save(this.eventRepository.findById(eventId));
        activeOrderRepository.save(activeOrder);
    }

    private ReservationResultDTO buildReservationResult(int eventId, int zoneId, int quantity, List<String> seatNumbers) {
        return new ReservationResultDTO(
                eventId,
                zoneId,
                quantity,
                seatNumbers,
                LocalDateTime.now().plusMinutes(this.reservationTimeoutMinutes)
        );
    }

    private void validateRemoveQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }



    public ActiveOrderDTO restoreActiveOrder(int userId) {
        ActiveOrder activeOrder = activeOrderRepository.getByUserId(userId);
        if (activeOrder == null) {
            log.info("No active order found for userId={}, returning null", userId);
            return null;
        }
        log.info("Active order found for userId={}, restoring ActiveOrderDTO", userId);
        ActiveOrderDTO activeOrderDTO = activeOrder.toDTO();
        // enrich the DTO with event and zone details for each line item (for better UX in the frontend; avoids extra calls from frontend to get event/zone details for each line)
        List<ActiveOrderDTO.CartLineDTO> enrichedLines = new ArrayList<>();
        for (ActiveOrderDTO.CartLineDTO line : activeOrderDTO.lines()) {
            Event event = eventRepository.findById(line.eventId());
            String eventName = (event != null) ? event.getName() : "Unknown Event";
            enrichedLines.add(new ActiveOrderDTO.CartLineDTO(
                    line.eventId(),
                    eventName,
                    line.zoneId(),
                    line.seatNumber(),
                    line.pricePerTicket(),
                    line.addedAt()));
        }
        // return the same DTO but with enriched lines (event names) for better frontend UX; the frontend can ignore the extra eventName field if it wants and just use eventId, or it can show the event name directly in the cart without needing to make extra calls to get event details for each line item
        return new ActiveOrderDTO(
                activeOrderDTO.userId(),
                activeOrderDTO.sessionId(),
                activeOrderDTO.createdAt(),
                activeOrderDTO.remainingSecondsBeforeExpiry(),
                activeOrderDTO.currentTotalPrice(),
                enrichedLines);
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