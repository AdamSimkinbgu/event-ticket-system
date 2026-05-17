package com.ticketing.system.Core.Domain.ActiveOrder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.ticketing.system.Core.Application.dto.ActiveOrderDTO;

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
public class ActiveOrder {

    private Integer userId;
    private String sessionId;
    private String status;
    private List<CartLineItem> items;

    public ActiveOrder(Integer userId, String sessionId) {
        if (userId == null && sessionId == null) {
            throw new IllegalArgumentException(
                    "ActiveOrder must have at least a userId or a sessionId");
        }
        this.userId = userId;
        this.sessionId = sessionId;
        this.items = new ArrayList<>();
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

    public void addReservation(int eventId, int zoneId, int quantity, double price, LocalDateTime addedAt) {
        for (int i = 1; i <= quantity; i = i + 1) {
            CartLineItem newItem = new CartLineItem(eventId, zoneId, price, addedAt);
            this.items.add(newItem);
        }
    }

    public List<CartLineItem> getItems() {
        return items;
    }

    public List<CartLineItem> ReturnToStock() {
        List<CartLineItem> returnToStock = new ArrayList<>();
        returnToStock.addAll(items);
        clear();
        return returnToStock;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public boolean hasExpiredItem() {
        for (CartLineItem item : items) {
            if (item.isExpired()) {
                return true;
            }
        }
        return false;
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
        if (isEmpty()) {
            throw new IllegalStateException("Cannot checkout an empty order");
        }
        if (hasExpiredItem()) {
            throw new IllegalStateException("Cannot checkout because one or more tickets expired");
        }
        return true;
    }

    public List<CartLineItem> buy() {
        List<CartLineItem> ticketToBUY = new ArrayList<>();
        if (isEmpty()) {
            throw new IllegalStateException("Cannot buy an empty order");
        }
        ticketToBUY.addAll(items);
        clear();
        return ticketToBUY;
    }

    public void clear() {
        items.clear();
    }

    public boolean hasReservationForEvent(int eventId) {
        for (CartLineItem item : items) {
            if (item.geteventId() == eventId && !item.isExpired()) {
                return true;
            }
        }
        return false;
    }

    public double getTotalPrice() {
        throw new UnsupportedOperationException("UC-9: not implemented");
    }

    public java.time.Duration getRemainingTime() {
        throw new UnsupportedOperationException("UC-5/9: not implemented");
    }



public void removeTickets(int eventId, int zoneId, int quantity) {
    if (quantity <= 0) {
        throw new IllegalArgumentException("Quantity must be positive");
    }

    int existingTickets = countTickets(eventId, zoneId);

    if (existingTickets < quantity) {
        throw new IllegalArgumentException("Not enough reserved tickets to remove");
    }

    int removedTickets = 0;

    for (int i =0 ; i <=items.size() - 1 && removedTickets < quantity; i = i + 1) {
        CartLineItem item = items.get(i);

        if (item.geteventId() == eventId && item.getzoneId() == zoneId) {
            items.remove(i);
            removedTickets = removedTickets + 1;
        }
    }
}



    public java.time.LocalDateTime getCreatedAt() {
        throw new UnsupportedOperationException("not implemented (add createdAt field)");
    }

    public void expire() {
        throw new UnsupportedOperationException("UC-2: not implemented");
    }

    public boolean hasTicket(int eventId, int zoneId) {
        for (CartLineItem item : items) {
            if (item.geteventId() == eventId && item.getzoneId() == zoneId) {
                return true;
            }
        }
        return false;
    }
  public int countTickets(int eventId, int zoneId) {
    int count = 0;

    for (CartLineItem item : items) {
        if (item.geteventId() == eventId && item.getzoneId() == zoneId) {
            count = count + 1;
        }
    }

    return count;
}

public ActiveOrderDTO toDTO() {
        List<ActiveOrderDTO.CartLineDTO> lineDTOs = new ArrayList<>();
        for (CartLineItem item : items) {
            lineDTOs.add(item.toDTO());
        }
        return new ActiveOrderDTO(
                getUserId(),
                null, // sessionId is null for Member active orders
                getCreatedAt(),
                this.getRemainingTime().getSeconds(),
                this.getTotalPrice(),
                lineDTOs);
    }

}
