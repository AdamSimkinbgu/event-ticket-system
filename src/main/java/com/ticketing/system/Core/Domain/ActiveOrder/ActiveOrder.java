package com.ticketing.system.Core.Domain.ActiveOrder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.ticketing.system.Core.Application.dto.ActiveOrderDTO;
import com.ticketing.system.Core.Domain.events.InventorySelection;
import com.ticketing.system.Core.Domain.shared.InvariantChecked;

/**
 * ActiveOrder = a cart in progress.
 *
 * <p>Carries a dual identity (auth rework / D9a):
 * <ul>
 *   <li><b>Guest cart:</b> {@code userId == null}, {@code sessionId != null}.
 *       Bound to a Guest Session row by sessionId; deleted when that Session
 *       is swept or ended.</li>
 *   <li><b>Member cart (active session):</b> {@code userId != null} and
 *       {@code sessionId != null}. The sessionId is the currently-attached
 *       Member session.</li>
 *   <li><b>Member cart (between sessions):</b> {@code userId != null},
 *       {@code sessionId == null}. Survives logout per II.3.1 / D9a;
 *       restored on next login by {@link #attachToSession(String)}.</li>
 * </ul>
 *
 * <p>Invariant: at least one of userId / sessionId is non-null at all times.
 */
public class ActiveOrder implements InvariantChecked {

    private Integer userId;
    private String sessionId;
    private String status;
    private final List<CartLineItem> items;
    private final LocalDateTime createdAt;
    private final Object itemsLock = new Object();

    public ActiveOrder(Integer userId, String sessionId) {
        if (userId == null && sessionId == null) {
            throw new IllegalArgumentException(
                    "ActiveOrder must have at least a userId or a sessionId");
        }
        this.userId = userId;
        this.sessionId = sessionId;
        this.items = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
    }





    

    /**
     * Generic reservation entry point used by application services.
     * Standing/seated branching stays inside the domain object instead of being
     * duplicated in ReservationService.
     */
    public void addReservation(int eventId, int zoneId, InventorySelection selection, double price,
            LocalDateTime addedAt) {
        if (selection == null) {
            throw new IllegalArgumentException("Inventory selection is required");
        }

        if (selection.isStandingSelection()) {
            addStandingReservation(eventId, zoneId, selection.getQuantity(), price, addedAt);
        } else {
            addSeatedReservation(eventId, zoneId, selection.getSeatNumbers(), price, addedAt);
        }
    }

    
    /**
     * Generic removal entry point used by application services.
     */
    public void removeReservation(int eventId, int zoneId, InventorySelection selection) {
        if (selection == null) {
            throw new IllegalArgumentException("Inventory selection is required");
        }

        if (selection.isStandingSelection()) {
            removeStandingSpots(eventId, zoneId, selection.getQuantity());
        } else {
            removeSeats(eventId, zoneId, selection.getSeatNumbers());
        }
    }





























    /**
     * Legacy convenience for existing Member-only callers. Equivalent to
     * {@code new ActiveOrder(userId, null)} — a Member cart in the
     * between-sessions state. New code should prefer
     * {@link #forMember(int, String)} or {@link #forGuest(String)}.
     */
    public ActiveOrder(int userId) {
        this(Integer.valueOf(userId), null);
    }


    /** Member cart attached to an active session. */
    public static ActiveOrder forMember(int userId, String sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("forMember requires a non-null sessionId");
        }
        return new ActiveOrder(Integer.valueOf(userId), sessionId);
    }


    /** Guest cart bound to a session (no userId yet). */
    public static ActiveOrder forGuest(String sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("forGuest requires a non-null sessionId");
        }
        return new ActiveOrder(null, sessionId);
    }












    // changed name from addReservation to addStandingReservation and added addSeatedReservation to support seated zones as well.
    public void addStandingReservation(int eventId, int zoneId, int quantity, double price, LocalDateTime addedAt) {
        synchronized (itemsLock) {
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }

            for (int i = 0; i < quantity; i++) {
                items.add(new CartLineItem(eventId, zoneId, null, price, addedAt));
            }
        }
    }


    public void addSeatedReservation(int eventId, int zoneId, List<String> seatNumbers, double price, LocalDateTime addedAt) {
        synchronized (itemsLock) {
            if (seatNumbers == null || seatNumbers.isEmpty()) {
                throw new IllegalArgumentException("Seat numbers must be non-empty");
            }
            // check that we're not adding duplicate seat numbers for the same event and zone that already exist in the active order.
            this.checkThatNotAddingDuplicateSeatNumbersForEventAndZone(eventId, zoneId, seatNumbers);

            for (String seatNumber : seatNumbers) {
                items.add(new CartLineItem(eventId, zoneId, seatNumber, price, addedAt));
            }
        }
    }


    












    public void removeStandingSpots(int eventId, int zoneId, int quantity) {
        synchronized (itemsLock) {
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }
            if (!hasReservationForEventWithoutLock(eventId)) {
                throw new IllegalArgumentException("Active order does not contain this event");
            }

            // check that enough standing tickets exist in the active order before attempting removal, to avoid partial removals if the quantity is greater than the number of reserved tickets.
            int existingTickets = countStandingTicketsWithoutLock(eventId, zoneId);

            if (existingTickets < quantity) {
                throw new IllegalArgumentException("Not enough reserved tickets to remove");
            }


            int removedCount = 0;
            for (int i = 0; i < items.size() && removedCount < quantity; i++) {
                CartLineItem item = items.get(i);
                if (item.geteventId() == eventId &&
                        item.getzoneId() == zoneId &&
                        item.getSeatNumber() == null && // only match standing tickets
                        !item.isExpired()) {
                    items.remove(i);
                    removedCount++;
                    i--; // adjust index after removal
                }
            }
        }
    }
    


    public void removeSeats(int eventId, int zoneId, List<String> seatNumbers) {
        this.checkThatSeatNumbersListDoesNotContainDuplicates(seatNumbers);

        synchronized (itemsLock) {
            if (!hasReservationForEventWithoutLock(eventId)) {
                throw new IllegalArgumentException("Active order does not contain this event");
            }

            // check that all seatNumbers exist in the active order before attempting removal, to avoid partial removals if an invalid seatNumber is provided.
            this.validateContainsSeats(eventId, zoneId, seatNumbers);

            // start removals
            for (String seatNumber : seatNumbers) {
                boolean removed = items.removeIf(item -> item.geteventId() == eventId &&
                        item.getzoneId() == zoneId &&
                        seatNumber.equals(item.getSeatNumber()) &&
                        !item.isExpired());

                if (!removed) {
                    // check just in case even though we already checked that the seat numbers do exist, to avoid silent failures if there's a bug in the validation logic.
                    throw new IllegalArgumentException("Seat not found in active order: " + seatNumber);
                }
            }
        }
    }


    









    /**
     * Validation-only method. This is useful before releasing inventory from the event,
     * so a bad remove request does not accidentally put tickets back in stock.
     */
    public void validateContainsReservation(int eventId, int zoneId, InventorySelection selection) {
        if (selection == null) {
            throw new IllegalArgumentException("Inventory selection is required");
        }

        synchronized (itemsLock) {
            if (!hasReservationForEventWithoutLock(eventId)) {
                throw new IllegalArgumentException("Active order does not contain this event");
            }

            if (selection.isStandingSelection()) {
                int existingTickets = countStandingTicketsWithoutLock(eventId, zoneId);
                if (existingTickets < selection.getQuantity()) {
                    throw new IllegalArgumentException("Not enough reserved tickets to remove");
                }
            } else {
                validateContainsSeats(eventId, zoneId, selection.getSeatNumbers());
            }
        }
    }













    private void checkThatNotAddingDuplicateSeatNumbersForEventAndZone(int eventId, int zoneId,
            List<String> seatNumbers) {
        for (String seatNumber : seatNumbers) {
            boolean exists = items.stream().anyMatch(item -> item.geteventId() == eventId &&
                    item.getzoneId() == zoneId &&
                    seatNumber.equals(item.getSeatNumber()) &&
                    !item.isExpired());

            if (exists) {
                throw new IllegalArgumentException(
                        "Duplicate seat number for event and zone being added even though it already exists in the active order: "
                                + seatNumber);
            }
        }
        // that there aren't duplicates in seatnumbers
        checkThatSeatNumbersListDoesNotContainDuplicates(seatNumbers);
    }
    

    private void checkThatSeatNumbersListDoesNotContainDuplicates(List<String> seatNumbers) {
        if (seatNumbers.size() != new HashSet<>(seatNumbers).size()) {
            throw new IllegalArgumentException("Duplicate seat numbers provided in input list: " + seatNumbers);
        }
    }


    

    public void validateContainsSeats(int eventId, int zoneId, List<String> seatNumbers) {
        synchronized (itemsLock) {
            for (String seatNumber : seatNumbers) {
                boolean exists = items.stream().anyMatch(item -> item.geteventId() == eventId &&
                        item.getzoneId() == zoneId &&
                        seatNumber.equals(item.getSeatNumber()) &&
                        !item.isExpired());

                if (!exists) {
                    throw new IllegalArgumentException("Active order does not contain seat: " + seatNumber);
                }
            }
        }
    }



    

    private int countStandingTicketsWithoutLock(int eventId, int zoneId) {
        int count = 0;

        for (CartLineItem item : items) {
            if (item.geteventId() == eventId &&
                    item.getzoneId() == zoneId &&
                    item.getSeatNumber() == null && // only count standing tickets
                    !item.isExpired()) {
                count = count + 1;
            }
        }

        return count;
    }





    


    public List<CartLineItem> getItems() {
        synchronized (itemsLock) {
            return new ArrayList<>(items);
        }
    }


    public List<CartLineItem> ReturnToStock() {
        synchronized (itemsLock) {
            List<CartLineItem> returnToStock = new ArrayList<>(items);
            items.clear();
            return returnToStock;
        }
    }

    public boolean isEmpty() {
        synchronized (itemsLock) {
            return items.isEmpty();
        }
    }
      public boolean hasExpiredItem() {
        synchronized (itemsLock) {
            for (CartLineItem item : items) {
                if (item.isExpired()) {
                    return true;
                }
            }

            return false;
        }
    }


    /**
     * Returns the userId, or {@code 0} for Guest carts. Preserved as
     * {@code int} for legacy callers (ReservationService, CheckoutService).
     * Prefer {@link #userIdOrNull()} when nullability matters.
     */
    public int getUserId() {
        return userId == null ? 0 : userId;
    }

    /** Returns the userId or {@code null} for Guest carts. */
    public Integer userIdOrNull() {
        return userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean isMember() {
        return userId != null;
    }

    public boolean isGuest() {
        return userId == null;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // returns the minimum remaining time among the items in the active order, which represents the time until the next item expires. If there are no items, returns Duration.ZERO.
    public Duration getRemainingTime() {
        synchronized (itemsLock) {
            if (items.isEmpty()) {
                return Duration.ZERO;
            }

            return items.stream()
                    .map(CartLineItem::getRemainingTime)
                    .min(Duration::compareTo)
                    .orElse(Duration.ZERO);
        }
    }

    // returns the total price of all items in the active order by summing up their individual prices at reservation time.
    // OR* avoid using domain total and let ReservationService build the DTO with event policies.   <------
    public double getRegularTotalPrice() {
        synchronized (itemsLock) {
            return items.stream()
                    .mapToDouble(CartLineItem::getPriceAtReservation)
                    .sum();
        }
    }



    /**
     * Guest→Member promotion: claim this cart for a user. Throws if the cart
     * already belongs to a different user.
     */
    public void attachToUser(int newUserId) {
        if (this.userId != null && this.userId != newUserId) {
            throw new IllegalStateException("cart already belongs to user " + this.userId);
        }
        this.userId = newUserId;
    }

    /**
     * Re-bind a (persistent) Member cart to a fresh session — used by D9a
     * restoration on next login.
     */
    public void attachToSession(String newSessionId) {
        if (newSessionId == null || newSessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must be non-blank");
        }
        this.sessionId = newSessionId;
    }

    /**
     * Marks the cart as orphaned from any active session — used on logout
     * for Member carts (D9a). The cart's userId is preserved; only the
     * sessionId pointer clears.
     */
    public void clearSession() {
        this.sessionId = null;
    }



  public boolean validateCanCheckout() {
        synchronized (itemsLock) {
            if (items.isEmpty()) {
                throw new IllegalStateException("Cannot checkout an empty order");
            }

            for (CartLineItem item : items) {
                if (item.isExpired()) {
                    throw new IllegalStateException("Cannot checkout because one or more tickets expired");
                }
            }

            return true;
        }
    }


 public List<CartLineItem> buy() {
        synchronized (itemsLock) {
            if (items.isEmpty()) {
                throw new IllegalStateException("Cannot buy an empty order");
            }

            List<CartLineItem> ticketToBuy = new ArrayList<>(items);
            items.clear();

            return ticketToBuy;
        }
    }

    public void clear() {
        synchronized (itemsLock) {
            items.clear();
        }
    }


 public boolean hasReservationForEvent(int eventId) {
        synchronized (itemsLock) {
            for (CartLineItem item : items) {
                if (item.geteventId() == eventId && !item.isExpired()) {
                    return true;
                }
            }

            return false;
        }
    }


    




    private boolean hasReservationForEventWithoutLock(int eventId) {
        for (CartLineItem item : items) {
            if (item.geteventId() == eventId && !item.isExpired()) {
                return true;
            }
        }
        return false;
    }


    public void expire() {
        throw new UnsupportedOperationException("UC-2: not implemented");
    }

 public boolean hasTicket(int eventId, int zoneId) {
        synchronized (itemsLock) {
            for (CartLineItem item : items) {
                if (item.geteventId() == eventId && item.getzoneId() == zoneId) {
                    return true;
                }
            }

            return false;
        }
    }




    public int countTickets(int eventId, int zoneId) {
        synchronized (itemsLock) {
            return countTicketsWithoutLock(eventId, zoneId);
        }
    }
    
    private int countTicketsWithoutLock(int eventId, int zoneId) {
        int count = 0;

        for (CartLineItem item : items) {
            if (item.geteventId() == eventId && item.getzoneId() == zoneId && !item.isExpired()) {
                count = count + 1;
            }
        }

        return count;
    }

    

    public ActiveOrderDTO toDTO() {
        synchronized (itemsLock) {
            List<ActiveOrderDTO.CartLineDTO> lineDTOs = new ArrayList<>();

            for (CartLineItem item : items) {
                lineDTOs.add(item.toDTO());
            }

            return new ActiveOrderDTO(
                    getUserId(),
                    sessionId,
                    getCreatedAt(),
                    this.getRemainingTime().getSeconds(),
                    this.getRegularTotalPrice(),
                    lineDTOs
            );
        }
    }

    @Override
    public void checkInvariants() {
        // The constructor already enforces "at least one identity present" — re-verify here.
        if (userId == null && sessionId == null) {
            throw new IllegalStateException(
                    "ActiveOrder invariant violated: must have userId or sessionId (or both)");
        }
        if (userId != null && userId <= 0) {
            throw new IllegalStateException(
                    "ActiveOrder invariant violated: userId must be positive when set (was " + userId + ")");
        }
        if (sessionId != null && sessionId.isBlank()) {
            throw new IllegalStateException("ActiveOrder invariant violated: sessionId must be non-blank when set");
        }
        synchronized (itemsLock) {
            if (items == null) {
                throw new IllegalStateException("ActiveOrder invariant violated: items list must not be null");
            }
            for (CartLineItem item : items) {
                if (item == null) {
                    throw new IllegalStateException("ActiveOrder invariant violated: items list contains null");
                }
            }
        }
    }


}
