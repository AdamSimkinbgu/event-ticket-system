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
    private final List<CartLineItem> items;
     private final Object itemsLock = new Object();
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
        synchronized (itemsLock) {
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }

            for (int i = 1; i <= quantity; i = i + 1) {
                CartLineItem newItem = new CartLineItem(eventId, zoneId, price, addedAt);
                this.items.add(newItem);
            }
        }
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
    
    public double getTotalPrice() {
        throw new UnsupportedOperationException("UC-9: not implemented");
    }

    public java.time.Duration getRemainingTime() {
        throw new UnsupportedOperationException("UC-5/9: not implemented");
    }


 public void removeTickets(int eventId, int zoneId, int quantity) {
        synchronized (itemsLock) {
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }

            int existingTickets = countTicketsWithoutLock(eventId, zoneId);

            if (existingTickets < quantity) {
                throw new IllegalArgumentException("Not enough reserved tickets to remove");
            }

            int removedTickets = 0;
            int i = 0;

            while (i < items.size() && removedTickets < quantity) {
                CartLineItem item = items.get(i);

                if (item.geteventId() == eventId && item.getzoneId() == zoneId) {
                    items.remove(i);
                    removedTickets = removedTickets + 1;
                } else {
                    i = i + 1;
                }
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
            if (item.geteventId() == eventId && item.getzoneId() == zoneId) {
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
                this.getTotalPrice(),
                lineDTOs
        );
    }
}
}
