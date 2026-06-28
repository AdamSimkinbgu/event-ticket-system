package com.ticketing.system.Presentation.presenters.order;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.ActiveOrderDTO;
import com.ticketing.system.Core.Application.dto.InventorySelectionDTO;
import com.ticketing.system.Core.Application.services.ReservationService;
import com.ticketing.system.Presentation.components.Money;
import com.ticketing.system.Presentation.session.SessionIdentity;

/**
 * MVP presenter for the cart view. Bridges the view to {@link ReservationService}
 * using the current {@link SessionIdentity}'s credential, and returns sealed
 * {@code Outcome} results the view renders. Holds no Vaadin imports so it stays
 * unit-testable in isolation.
 */
@Component
public class CartPresenter {

    private final ReservationService reservationService;
    private final SessionIdentity identity;

    @Autowired
    public CartPresenter(ReservationService reservationService, SessionIdentity identity) {
        this.reservationService = reservationService;
        this.identity = identity;
    }

    /**
     * Loads the current buyer's active order for display.
     *
     * @return {@link LoadOutcome.NotAuthenticated} if there is no credential,
     *         {@link LoadOutcome.Empty} if the cart is empty, {@link LoadOutcome.Shown}
     *         with the cart and subtotal otherwise, or {@link LoadOutcome.Failure}
     *         on error
     */
    public LoadOutcome loadCart() {
        String credential = identity.credential();
        if (credential == null || credential.isBlank()) {
            return new LoadOutcome.NotAuthenticated();
        }
        try {
            ActiveOrderDTO order = reservationService.viewMyActiveOrder(credential);
            if (order == null || order.lines().isEmpty()) {
                return new LoadOutcome.Empty();
            }
            return new LoadOutcome.Shown(toVM(order), subtotalCents(order));
        } catch (RuntimeException e) {
            return new LoadOutcome.Failure(e.getMessage());
        }
    }

    /**
     * Removes a single line from the cart and returns the updated cart. Removing
     * the last line yields an empty cart, not an error.
     *
     * @param line the line to remove (its seat number selects seated vs standing)
     * @return {@link RemoveOutcome.Removed} with the updated cart and subtotal, or
     *         {@link RemoveOutcome.Failure} if no line/credential or on error
     */
    public RemoveOutcome removeLine(CartVM.LineVM line) {
        if (line == null) {
            return new RemoveOutcome.Failure("No line selected");
        }
        String credential = identity.credential();
        if (credential == null || credential.isBlank()) {
            return new RemoveOutcome.Failure("Your session has expired");
        }
        try {
            InventorySelectionDTO selection = (line.seatNumber() != null)
                ? InventorySelectionDTO.seated(List.of(line.seatNumber()))
                : InventorySelectionDTO.standing(1);

            reservationService.removeLine(credential, line.eventId(), line.zoneId(), selection);

            ActiveOrderDTO updated = reservationService.viewMyActiveOrder(credential);
            if (updated == null || updated.lines().isEmpty()) {
                // Removing the last line empties the order — return an empty cart, not an error.
                return new RemoveOutcome.Removed(new CartVM(List.of(), 0L), 0L);
            }
            return new RemoveOutcome.Removed(toVM(updated), subtotalCents(updated));
        } catch (RuntimeException e) {
            return new RemoveOutcome.Failure(e.getMessage());
        }
    }

    /**
     * @param order the active order DTO
     * @return the cart view-model (lines + remaining time before expiry)
     */
    private CartVM toVM(ActiveOrderDTO order) {
        List<CartVM.LineVM> lines = order.lines().stream()
            .map(l -> new CartVM.LineVM(
                l.eventName(), l.seatNumber(), l.pricePerTicket(),
                l.eventId(), l.zoneId()))
            .toList();
        return new CartVM(lines, order.remainingSecondsBeforeExpiry());
    }

    /**
     * @param order the active order DTO
     * @return the order subtotal in integer cents (0 for an empty/absent order)
     */
    private long subtotalCents(ActiveOrderDTO order) {
        if (order == null || order.lines().isEmpty()) return 0L;
        return order.lines().stream()
                .mapToLong(l -> Money.toCents(l.pricePerTicket()))
                .sum();
    }

    /** View-model for the cart: its lines and the seconds left before the reservation expires. */
    public record CartVM(List<LineVM> lines, long remainingSecondsBeforeExpiry) {
        /** One cart line: display fields plus the event/zone ids needed to remove it. */
        public record LineVM(
            String eventName,
            String seatNumber,
            double pricePerTicket,
            int eventId,
            int zoneId
        ) {}
    }

    /** Result of {@link #loadCart()}. */
    public sealed interface LoadOutcome {
        /** The cart has contents: render the lines and subtotal. */
        record Shown(CartVM cart, long subtotalCents)        implements LoadOutcome { }
        /** The cart is empty: render the empty state. */
        record Empty()                                       implements LoadOutcome { }
        /** No active credential: prompt the buyer to sign in / start a session. */
        record NotAuthenticated()                            implements LoadOutcome { }
        /** Loading failed: show the reason. */
        record Failure(String reason)                        implements LoadOutcome { }
    }

    /** Result of {@link #removeLine(CartVM.LineVM)}. */
    public sealed interface RemoveOutcome {
        /** The line was removed: render the updated cart and subtotal. */
        record Removed(CartVM cart, long subtotalCents)      implements RemoveOutcome { }
        /** Removal failed: show the reason. */
        record Failure(String reason)                        implements RemoveOutcome { }
    }
}