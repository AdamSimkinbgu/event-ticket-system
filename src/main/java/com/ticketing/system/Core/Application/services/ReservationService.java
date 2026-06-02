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
            // Validate event and zone exist before doing anything else (e.g. before creating an active order if needed, before checking inventory, etc.) to fail fast on invalid input and avoid doing unnecessary work or creating entities that we will not use if the event/zone is invalid. This also ensures that we do not create an active order for a buyer if the event or zone they are trying to reserve does not exist, which keeps our data cleaner and avoids having orphaned active orders that are not associated with any valid events or zones.
            event = getEventOrThrow(eventId);
            InventoryZone zone = getZoneOrThrow(event, zoneId);
            activeOrder = getOrCreateActiveOrder(buyer);

            double pricePerTicket = zone.getprice();

            // Reserve inventory first before modifying the active order, so that if we fail to reserve inventory (e.g. not enough tickets available), we do not end up with an active order that has reservations that were not successfully made. This also ensures that we only modify the active order if we are able to successfully reserve the requested tickets, which keeps our data consistent and avoids having active orders that reflect reservations that do not actually exist.
            event.reserveInventory(zoneId, selection);
            inventoryReserved = true;

            // Now that we have successfully reserved the inventory, we can safely modify the active order to reflect the new reservation. If any of the validations for modifying the active order fail (e.g. trying to reserve more tickets than allowed by purchase policy, etc.), we will throw an exception and roll back the inventory reservation in the catch block, ensuring that we do not end up with an active order that has reservations that were not successfully made.
            activeOrder.addReservation(eventId, zoneId, selection, pricePerTicket, LocalDateTime.now());
            orderModified = true;

            eventRepository.save(event);
            activeOrderRepository.save(activeOrder);
            // Notify the member of the successful reservation. For guests, we do not have a user ID to send notifications to, so we skip this step for guests. The notification can be used to trigger email notifications, app push notifications, etc. to inform the member of their successful reservation and any details they may need (e.g. event name, zone, quantity reserved, etc.).
            notifyReservationSuccessIfMember(buyer, eventId, zoneId, selection.getQuantity());
            // Return the reservation result, which includes details about the reservation such as event ID, zone ID, quantity reserved, seat numbers if applicable, and the expiration time of the reservation (based on the current time plus the configured reservation timeout). This information can be used by the frontend to show the user their current reservations and how long they have before they expire, etc.
            return buildReservationResult(eventId, zoneId, selection);

        } catch (RuntimeException e) {
            // If any exception occurs during the reservation process, we need to roll back any actions that were taken to keep our data consistent. This includes releasing any inventory that was reserved and removing any reservations that were added to the active order. We also log the error for monitoring and debugging purposes, and rethrow the exception to indicate that the reservation failed. The frontend can catch this exception and show an appropriate error message to the user (e.g. "Failed to reserve tickets: not enough availability", "Failed to reserve tickets: event not found", "Failed to reserve tickets: invalid input", etc.).
            rollbackReservationIfNeeded(event, activeOrder, eventId, zoneId, selection, inventoryReserved, orderModified);
            // Notify the member of the failed reservation attempt. For guests, we do not have a user ID to send notifications to, so we skip this step for guests. The notification can be used to trigger email notifications, app push notifications, etc. to inform the member that their reservation attempt failed and provide any details that may be relevant (e.g. event name, zone, quantity they tried to reserve, reason for failure if known, etc.).
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
        // Validate input arguments before doing anything else to fail fast on invalid input and avoid doing unnecessary work. This also ensures that we do not attempt to remove reservations for an event or zone that does not exist, which keeps our data consistent and avoids having active orders that reflect removals for events or zones that do not actually exist.
        validateReservationArguments(eventId, zoneId, selection);

        String orderLockKey = buyer.isMember()
                ? "user:" + buyer.userId()
                : "sess:" + buyer.sessionId();

        activeOrderRepository.lockForUpdate(orderLockKey);
        eventRepository.lockForUpdate(eventId);

        try {
            // Validate event and zone exist before doing anything else (e.g. before checking the active order, etc.) to fail fast on invalid input and avoid doing unnecessary work or creating entities that we will not use if the event/zone is invalid. This also ensures that we do not attempt to remove reservations for an event or zone that does not exist, which keeps our data consistent and avoids having active orders that reflect removals for events or zones that do not actually exist.
            Event event = getEventOrThrow(eventId);
            getZoneOrThrow(event, zoneId); // validates venue map + zone exists before touching the order
            ActiveOrder activeOrder = getActiveOrderOrThrow(buyer);

            // Validate first so we do not release inventory for tickets that are not in the active order.
            activeOrder.validateContainsReservation(eventId, zoneId, selection);
            // Now that we have validated that the active order contains the reservation we are trying to remove, we can safely release the inventory and modify the active order to reflect the removed reservation. If any of the validations for releasing inventory or modifying the active order fail (e.g. trying to remove more tickets than are reserved in the active order, etc.), we will throw an exception and roll back any changes in the catch block, ensuring that we do not end up with an active order that reflects removals that were not successfully made or inventory that was released without a corresponding reservation in the active order.
            event.releaseInventory(zoneId, selection);
            // Note: for simplicity, we assume that the price per ticket does not change when removing a reservation. If there are any discounts or promotions that apply to the reservation, we would need to handle that logic here as well to ensure that the active order reflects the correct pricing after the reservation is removed.
            activeOrder.removeReservation(eventId, zoneId, selection);

            eventRepository.save(event);
            activeOrderRepository.save(activeOrder);

            // Notify the member of the successful removal. For guests, we do not have a user ID to send notifications to, so we skip this step for guests. The notification can be used to trigger email notifications, app push notifications, etc. to inform the member of their successful reservation removal and any details they may need (e.g. event name, zone, quantity removed, etc.).
            notifyRemoveSuccessIfMember(buyer, eventId, zoneId, selection.getQuantity());

            // Return the reservation result, which includes details about the removed reservation such as event ID, zone ID, quantity removed, seat numbers if applicable, and the expiration time of the reservation (based on the current time plus the configured reservation timeout). This information can be used by the frontend to show the user their current reservations and how long they have before they expire, etc.
            return buildReservationResult(eventId, zoneId, selection);

        } catch (RuntimeException e) {
            // If any exception occurs during the removal process, we need to roll back any actions that were taken to keep our data consistent. This includes re-reserving any inventory that was released and re-adding any reservations that were removed from the active order. We also log the error for monitoring and debugging purposes, and rethrow the exception to indicate that the removal failed. The frontend can catch this exception and show an appropriate error message to the user (e.g. "Failed to remove reservation: reservation not found in active order", "Failed to remove reservation: event not found", "Failed to remove reservation: invalid input", etc.).
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

    // Helper methods to authenticate the buyer based on the type of request (member vs guest) and extract the necessary information to create a BuyerContextDTO, which is used in the main flows to represent the buyer's context (e.g. whether they are a member or guest, their user ID if they are a member, their session ID if they are a guest, etc.). These methods also handle validation of the input tokens/session IDs and throw appropriate exceptions if the authentication fails (e.g. invalid token, expired token, missing session ID, etc.), which can be caught by the frontend to show appropriate error messages to the user.
    
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

    

    // Validation of the main input arguments for both reserve and remove flows. This method is called at the beginning of both flows to ensure that the input is valid before we do any work. This helps us fail fast on invalid input and avoid doing unnecessary work or creating entities that we will not use if the input is invalid. It also ensures that we do not attempt to reserve or remove inventory for an event or zone that does not exist, which keeps our data consistent and avoids having active orders that reflect reservations or removals for events or zones that do not actually exist.
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

    // Helper method to get the event from the repository and validate that it exists. This is used in both reserve and remove flows to ensure that we are working with a valid event before we attempt to reserve or remove inventory, which keeps our data consistent and avoids having active orders that reflect reservations or removals for events that do not actually exist. By centralizing this logic in a helper method, we also avoid code duplication and keep the main flows cleaner and more focused on the reservation and removal logic rather than the details of how we look up events from the repository.
    private Event getEventOrThrow(int eventId) {
        Event event = eventRepository.findById(eventId);

        if (event == null) {
            log.warn("Request rejected: event not found. eventId={}", eventId);
            throw new IllegalArgumentException("Event not found: " + eventId);
        }

        return event;
    }


    // Helper method to get the inventory zone from the event's venue map and validate that it exists. This is used in both reserve and remove flows to ensure that we are working with a valid event and zone before we attempt to reserve or remove inventory, which keeps our data consistent and avoids having active orders that reflect reservations or removals for events or zones that do not actually exist. By centralizing this logic in a helper method, we also avoid code duplication and keep the main flows cleaner and more focused on the reservation and removal logic rather than the details of how we look up zones from events.
    private InventoryZone getZoneOrThrow(Event event, int zoneId) {
        if (event.getVenueMap() == null) {
            throw new IllegalStateException("Venue map is not configured for event: " + event.getId());
        }

        InventoryZone zone;
        try {
            zone = event.getVenueMap().getZone(zoneId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Zone not found: " + zoneId);
        }

        if (zone == null) {
            throw new IllegalArgumentException("Zone not found: " + zoneId);
        }

        return zone;
    }

    // For members, we get or create an active order based on their user ID. For guests, we get or create an active order based on their session ID. This method abstracts away the logic of determining whether to use user ID or session ID and ensures that we always have an active order to work with in the main flows, which simplifies the logic in those flows and keeps them focused on the reservation and removal logic rather than the details of how we manage active orders for different types of buyers.
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


    // For members, we get the active order based on their user ID. For guests, we get the active order based on their session ID. This method abstracts away the logic of determining whether to use user ID or session ID and ensures that we can easily retrieve the active order for the buyer in the main flows, which simplifies the logic in those flows and keeps them focused on the reservation and removal logic rather than the details of how we manage active orders for different types of buyers. We also throw an exception if no active order is found for the buyer, which allows us to handle that case in the main flows (e.g. show an error message to the user if they try to remove a reservation but they do not have an active order).
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


    // Helper method to roll back any changes made during the reservation or removal process if an exception occurs, in order to keep our data consistent. This includes releasing any inventory that was reserved and re-adding any reservations that were removed from the active order. We also log any exceptions that occur during the rollback process for monitoring and debugging purposes, but we do not rethrow those exceptions since we want to ensure that the original exception from the reservation or removal process is what gets propagated to indicate the failure of the operation, while still making a best effort to roll back any changes to keep our data consistent.
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


    // Helper method to build the reservation result DTO that is returned by the reserve and remove methods, which includes details about the reservation such as event ID, zone ID, quantity reserved/removed, seat numbers if applicable, and the expiration time of the reservation (based on the current time plus the configured reservation timeout). This information can be used by the frontend to show the user their current reservations and how long they have before they expire, etc.
    private ReservationResultDTO buildReservationResult(int eventId, int zoneId, InventorySelection selection) {
        return new ReservationResultDTO(
                eventId,
                zoneId,
                selection.getQuantity(),
                selection.getSeatNumbers(),
                LocalDateTime.now().plusMinutes(this.reservationTimeoutMinutes));
    }















    // method to restore an active order for a user (member or guest) - this is used by the frontend when a user logs in or returns to the site after navigating away, to restore their active order and show them their current reservations in the cart. For members, we look up the active order by their user ID. For guests, we look up the active order by their session ID. If an active order is found, we convert it to an ActiveOrderDTO and return it. If no active order is found, we return null. We also enrich the DTO with event names for better UX in the frontend, so that the frontend can show the event names directly in the cart without needing to make extra calls to get event details for each line item.
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



    // Helper method to abandon the active order for a user (member or guest) - this is used by the frontend when a user explicitly clicks "Abandon Cart" or when they log out, to clear their active order and release any reserved inventory back to the events. For members, we look up the active order by their user ID. For guests, we look up the active order by their session ID. If an active order is found, we release any reserved inventory back to the events and then delete the active order. If no active order is found, we simply return without doing anything. This ensures that we do not leave any reserved inventory hanging around for abandoned carts, which keeps our inventory accurate and allows other users to purchase those tickets if they are still available.
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


    // Helper method to expire active orders that have passed their expiration time. This is used by a background job that runs periodically (e.g. every minute) to check for any active orders that have expired and release their reserved inventory back to the events, and then delete those active orders. This ensures that we do not have any active orders hanging around indefinitely that have expired and are no longer valid, which keeps our data clean and our inventory accurate.
    public void expireActiveOrders() {
        throw new UnsupportedOperationException("UC-2: not implemented");
    }


    // Helper method to view the current active order for a user (member or guest). For members, we look up the active order by their user ID. For guests, we look up the active order by their session ID. If an active order is found, we convert it to an ActiveOrderDTO and return it. If no active order is found, we return null. This allows the frontend to show the user their current reservations in the cart when they navigate to the cart page, etc.
    public ActiveOrderDTO viewMyActiveOrder(String userOrSessionId) {
        throw new UnsupportedOperationException("UC-5/9: not implemented");
    }


}