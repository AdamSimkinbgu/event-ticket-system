package com.ticketing.system.Core.Application.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.ticketing.system.Core.Application.dto.ActiveOrderDTO;
import com.ticketing.system.Core.Application.dto.ReservationResultDTO;
import com.ticketing.system.Core.Application.dto.BuyerContextDTO;
import com.ticketing.system.Core.Application.dto.InventorySelectionDTO;
import com.ticketing.system.Core.Application.interfaces.INotificationService;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Domain.events.InventorySelection;
import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;
import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
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



    

    // ---------------------------------------------------------------------
    // Public API - use these methods from new code/controllers/tests
    // ---------------------------------------------------------------------

    public ReservationResultDTO reserveForMember(String token, int eventId, int zoneId, InventorySelectionDTO selectionDto) {
        return reserve(authenticateMember(token), eventId, zoneId, toDomainSelection(selectionDto));
    }

    public ReservationResultDTO reserveForGuest(String sessionId, int eventId, int zoneId, InventorySelectionDTO selectionDto) {
        return reserve(authenticateGuest(sessionId), eventId, zoneId, toDomainSelection(selectionDto));
    }

    public ReservationResultDTO removeForMember(String token, int eventId, int zoneId, InventorySelectionDTO selectionDto) {
        return remove(authenticateMember(token), eventId, zoneId, toDomainSelection(selectionDto));
    }

    public ReservationResultDTO removeForGuest(String sessionId, int eventId, int zoneId, InventorySelectionDTO selectionDto) {
        return remove(authenticateGuest(sessionId), eventId, zoneId, toDomainSelection(selectionDto));
    }

    private InventorySelection toDomainSelection(InventorySelectionDTO selectionDto) {
        if (selectionDto == null) {
            throw new IllegalArgumentException("Inventory selection is required");
        }

        return selectionDto.toDomainSelection();
    }






    // // ---------------------------------------------------------------------
    // // Backward-compatible wrappers - keep existing tests/callers working
    // // ---------------------------------------------------------------------

    // public ReservationResultDTO reserveStandingTicketsForMember(String token, int eventId, int zoneId, int quantity) {
    //     return reserveForMember(token, eventId, zoneId, InventorySelectionDTO.standing(quantity));
    // }

    // public ReservationResultDTO reserveStandingTicketsForGuest(String sessionId, int eventId, int zoneId, int quantity) {
    //     return reserveForGuest(sessionId, eventId, zoneId, InventorySelectionDTO.standing(quantity));
    // }

    // public ReservationResultDTO reserveSeatsForMember(String token, int eventId, int zoneId, List<String> seatNumbers) {
    //     return reserveForMember(token, eventId, zoneId, InventorySelectionDTO.seated(seatNumbers));
    // }

    // public ReservationResultDTO reserveSeatsForGuest(String sessionId, int eventId, int zoneId, List<String> seatNumbers) {
    //     return reserveForGuest(sessionId, eventId, zoneId, InventorySelectionDTO.seated(seatNumbers));
    // }

    // public ReservationResultDTO removeReservedStandingSpotsForMember(String token, int eventId, int zoneId, int quantity) {
    //     return removeForMember(token, eventId, zoneId, InventorySelectionDTO.standing(quantity));
    // }

    // public ReservationResultDTO removeReservedStandingSpotsForGuest(String sessionId, int eventId, int zoneId, int quantity) {
    //     return removeForGuest(sessionId, eventId, zoneId, InventorySelectionDTO.standing(quantity));
    // }

    // public ReservationResultDTO removeReservedSeatsForMember(String token, int eventId, int zoneId, List<String> seatNumbers) {
    //     return removeForMember(token, eventId, zoneId, InventorySelectionDTO.seated(seatNumbers));
    // }

    // public ReservationResultDTO removeReservedSeatsForGuest(String sessionId, int eventId, int zoneId, List<String> seatNumbers) {
    //     return removeForGuest(sessionId, eventId, zoneId, InventorySelectionDTO.seated(seatNumbers));
    // }

    










    // ---------------------------------------------------------------------
    // Unified flows of reserve & remove
    // ---------------------------------------------------------------------


    private ReservationResultDTO reserve(BuyerContextDTO buyer, int eventId, int zoneId, InventorySelection selection) {
        log.info("Entered reserve: buyerType={}, eventId={}, zoneId={}, quantity={}, seats={}",
                buyer.isMember() ? "MEMBER" : "GUEST",
                eventId,
                zoneId,
                selection == null ? null : selection.getQuantity(),
                selection == null ? null : selection.getSeatNumbers());

        validateReservationArguments(eventId, zoneId, selection);

        Event event = null;
        ActiveOrder activeOrder = null;
        boolean inventoryReserved = false;
        boolean orderModified = false;

        String orderLockKey = buyer.isMember()
                ? "user:" + buyer.userId()
                : "sess:" + buyer.sessionId();

        activeOrderRepository.lockForUpdate(orderLockKey);
        eventRepository.lockForUpdate(eventId);

        try {
            event = getEventOrThrow(eventId);
            InventoryZone zone = getZoneOrThrow(event, zoneId);
            activeOrder = getOrCreateActiveOrder(buyer);

            double pricePerTicket = zone.getprice();

            event.reserveInventory(zoneId, selection);
            inventoryReserved = true;

            activeOrder.addReservation(eventId, zoneId, selection, pricePerTicket, LocalDateTime.now());
            orderModified = true;

            eventRepository.save(event);
            activeOrderRepository.save(activeOrder);

            notifyReservationSuccessIfMember(buyer, eventId, zoneId, selection.getQuantity());

            return buildReservationResult(eventId, zoneId, selection);

        } catch (RuntimeException e) {
            rollbackReservationIfNeeded(event, activeOrder, eventId, zoneId, selection, inventoryReserved,
                    orderModified);

            notifyReservationFailureIfMember(buyer, eventId, zoneId, "Reservation failed: " + e.getMessage());

            log.warn("reserve failed: eventId={}, zoneId={}, selectionQuantity={}, seats={}, reason={}",
                    eventId, zoneId,
                    selection == null ? null : selection.getQuantity(),
                    selection == null ? null : selection.getSeatNumbers(),
                    e.getMessage());

            throw e;
        } finally {
            eventRepository.unlock(eventId);
            activeOrderRepository.unlock(orderLockKey);
        }
    }

    


    private ReservationResultDTO remove(BuyerContextDTO buyer, int eventId, int zoneId, InventorySelection selection) {
        log.info("Entered remove: buyerType={}, eventId={}, zoneId={}, quantity={}, seats={}",
                buyer.isMember() ? "MEMBER" : "GUEST",
                eventId,
                zoneId,
                selection == null ? null : selection.getQuantity(),
                selection == null ? null : selection.getSeatNumbers());

        validateReservationArguments(eventId, zoneId, selection);

        String orderLockKey = buyer.isMember()
                ? "user:" + buyer.userId()
                : "sess:" + buyer.sessionId();

        activeOrderRepository.lockForUpdate(orderLockKey);
        eventRepository.lockForUpdate(eventId);

        try {
            Event event = getEventOrThrow(eventId);
            getZoneOrThrow(event, zoneId); // validates venue map + zone exists before touching the order

            ActiveOrder activeOrder = getActiveOrderOrThrow(buyer);

            // Validate first so we do not release inventory for tickets that are not in the active order.
            activeOrder.validateContainsReservation(eventId, zoneId, selection);

            event.releaseInventory(zoneId, selection);
            activeOrder.removeReservation(eventId, zoneId, selection);

            eventRepository.save(event);
            activeOrderRepository.save(activeOrder);

            notifyRemoveSuccessIfMember(buyer, eventId, zoneId, selection.getQuantity());

            return buildReservationResult(eventId, zoneId, selection);

        } catch (RuntimeException e) {
            notifyRemoveFailureIfMember(buyer, eventId, zoneId, "Remove reservation failed: " + e.getMessage());

            log.warn("remove failed: eventId={}, zoneId={}, selectionQuantity={}, seats={}, reason={}",
                    eventId, zoneId,
                    selection == null ? null : selection.getQuantity(), selection == null ? null : selection.getSeatNumbers(),
                    e.getMessage());

            throw e;
        } finally {
            eventRepository.unlock(eventId);
            activeOrderRepository.unlock(orderLockKey);
        }
    }

    






    // ---------------------------------------------------------------------
    // Authentication / request parsing
    // ---------------------------------------------------------------------

    private BuyerContextDTO authenticateMember(String token) {
        return BuyerContextDTO.member(validateTokenAndGetUserId(token));
    }

    private BuyerContextDTO authenticateGuest(String sessionId) {
        validateSessionId(sessionId);
        return BuyerContextDTO.guest(sessionId);
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
            log.warn("Request rejected: missing session ID");
            throw new IllegalArgumentException("Missing session ID");
        }
    }

    


    private void validateReservationArguments(int eventId, int zoneId, InventorySelection selection) {
        if (eventId <= 0) {
            throw new IllegalArgumentException("eventId must be positive");
        }

        if (zoneId <= 0) {
            throw new IllegalArgumentException("zoneId must be positive");
        }

        if (selection == null) {
            throw new IllegalArgumentException("Inventory selection is required");
        }

        if (selection.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        if (selection.isSeatedSelection()) {
            for (String seatNumber : selection.getSeatNumbers()) {
                if (seatNumber == null || seatNumber.isBlank()) {
                    throw new IllegalArgumentException("Seat number must be non-blank");
                }
            }
        }
    }




    // ---------------------------------------------------------------------
    // helper functions - these are not part of the main flows but are used by multiple public methods, so they help keep the main flows clean and avoid code duplication.
    // ---------------------------------------------------------------------


    private Event getEventOrThrow(int eventId) {
        Event event = eventRepository.findById(eventId);

        if (event == null) {
            log.warn("Request rejected: event not found. eventId={}", eventId);
            throw new IllegalArgumentException("Event not found: " + eventId);
        }

        return event;
    }


    private InventoryZone getZoneOrThrow(Event event, int zoneId) {
        if (event.getVenueMap() == null) {
            throw new IllegalStateException("Venue map is not configured for event: " + event.getId());
        }

        try {
            return event.getVenueMap().getZone(zoneId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Zone not found: " + zoneId);
        }
    }


    private ActiveOrder getOrCreateActiveOrder(BuyerContextDTO buyer) {
        if (buyer.isMember()) {
            ActiveOrder activeOrder = activeOrderRepository.getByUserId(buyer.userId());
            if (activeOrder == null) {
                log.info("No active order found for userId={}, creating new ActiveOrder", buyer.userId());
                activeOrder = new ActiveOrder(buyer.userId());
            }
            return activeOrder;
        }

        return activeOrderRepository.getBySessionId(buyer.sessionId())
                .orElseGet(() -> {
                    log.info("No active order found for sessionId={}, creating new ActiveOrder", buyer.sessionId());
                    return ActiveOrder.forGuest(buyer.sessionId());
                });
    }


    private ActiveOrder getActiveOrderOrThrow(BuyerContextDTO buyer) {
        if (buyer.isMember()) {
            ActiveOrder activeOrder = activeOrderRepository.getByUserId(buyer.userId());
            if (activeOrder == null) {
                throw new IllegalArgumentException("Active order not found");
            }
            return activeOrder;
        }

        return activeOrderRepository.getBySessionId(buyer.sessionId())
                .orElseThrow(() -> new IllegalArgumentException("Active order not found"));
    }


    // ---------------------------------------------------------------------
    // Rollback / notifications / DTOs
    // ---------------------------------------------------------------------


    private void rollbackReservationIfNeeded(Event event, ActiveOrder activeOrder, int eventId, int zoneId,
                    InventorySelection selection, boolean inventoryReserved, boolean orderModified) {

        if (!inventoryReserved) {
            return;
        }

        try {
            if (orderModified && activeOrder != null) {
                activeOrder.removeReservation(eventId, zoneId, selection);
                activeOrderRepository.save(activeOrder);
            }
        } catch (RuntimeException ignored) {
            // best-effort rollback; the main exception is rethrown by caller
        }

        try {
            if (event != null) {
                event.releaseInventory(zoneId, selection);
                eventRepository.save(event);
            }
        } catch (RuntimeException ignored) {
            // best-effort rollback; the main exception is rethrown by caller
        }
    }




    private void notifyReservationSuccessIfMember(BuyerContextDTO buyer, int eventId, int zoneId, int quantity) {
        if (buyer.isMember()) {
            notificationService.notifyTicketReservationSuccess(buyer.userId(), eventId, zoneId, quantity);
        }
    }


    private void notifyReservationFailureIfMember(BuyerContextDTO buyer, int eventId, int zoneId, String reason) {
        if (buyer != null && buyer.isMember()) {
            notificationService.notifyTicketReservationFailure(buyer.userId(), eventId, zoneId, reason);
        }
    }


    private void notifyRemoveSuccessIfMember(BuyerContextDTO buyer, int eventId, int zoneId, int quantity) {
        if (buyer.isMember()) {
            notificationService.notifyRemoveTicketReservationSuccess(buyer.userId(), eventId, zoneId, quantity);
        }
    }


    private void notifyRemoveFailureIfMember(BuyerContextDTO buyer, int eventId, int zoneId, String reason) {
        if (buyer != null && buyer.isMember()) {
            notificationService.notifyRemoveTicketReservationFailure(buyer.userId(), eventId, zoneId, reason);
        }
    }


    private ReservationResultDTO buildReservationResult(int eventId, int zoneId, InventorySelection selection) {
        return new ReservationResultDTO(
                eventId,
                zoneId,
                selection.getQuantity(),
                selection.getSeatNumbers(),
                LocalDateTime.now().plusMinutes(this.reservationTimeoutMinutes));
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
        ActiveOrder activeOrder = activeOrderRepository.getByUserId(Integer.parseInt(userId));
        
        if (activeOrder != null) {
            // release inventory back to events before deleting the active order
            for (ActiveOrderDTO.CartLineDTO line : activeOrder.toDTO().lines()) {
                try {
                    Event event = eventRepository.findById(line.eventId());
                    if (event != null) {
                        InventorySelection selection = (line.seatNumber() != null)
                                ? InventorySelection.seated(List.of(line.seatNumber()))
                                : InventorySelection.standing(1);
                        event.releaseInventory(line.zoneId(), selection);
                        eventRepository.save(event);
                    }
                } catch (RuntimeException e) {
                    log.warn("Failed to release inventory for eventId={}, zoneId={}, seatNumber={}: {}",
                            line.eventId(), line.zoneId(), line.seatNumber(), e.getMessage());
                    // continue trying to release other lines even if one fails; we will still delete the active order to avoid leaving it in an inconsistent state, and the failed release can be cleaned up later by an admin or a background job
                }
            }
            activeOrderRepository.delete(activeOrder);
            log.info("Abandoned active order for userId={}", userId);

        } else {
            log.info("No active order to abandon for userId={}", userId);
        }

    }



    public void expireActiveOrders() {
        throw new UnsupportedOperationException("UC-2: not implemented");
    }



    public ActiveOrderDTO viewMyActiveOrder(String userOrSessionId) {
        throw new UnsupportedOperationException("UC-5/9: not implemented");
    }


}