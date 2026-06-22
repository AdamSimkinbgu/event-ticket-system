package com.ticketing.system.Presentation.presenters.order;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.ActiveOrderDTO;
import com.ticketing.system.Core.Application.dto.InventorySelectionDTO;
import com.ticketing.system.Core.Application.services.ReservationService;

/**
 * MVP presenter for {@code CartView}. No Vaadin imports — the outcome → UI
 * translation lives in the view; the service-call decision tree is unit-testable.
 */
@Component
public class CartPresenter {

    private final ReservationService reservationService;

    @Autowired
    public CartPresenter(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /** Load the caller's active order (member token OR guest session id). */
    public LoadOutcome loadCart(String credential) {
        if (credential == null || credential.isBlank()) {
            return new LoadOutcome.NotAuthenticated();
        }
        try {
            ActiveOrderDTO order = reservationService.viewMyActiveOrder(credential);
            if (order == null || order.lines().isEmpty()) {
                return new LoadOutcome.Empty();
            }
            return new LoadOutcome.Shown(order);
        } catch (RuntimeException e) {
            return new LoadOutcome.Failure(e.getMessage());
        }
    }

    /** Remove one cart line; returns the refreshed order on success. */
    public RemoveOutcome removeLine(String credential, ActiveOrderDTO.CartLineDTO line) {
        try {
            InventorySelectionDTO selection = (line.seatNumber() != null)
                ? InventorySelectionDTO.seated(List.of(line.seatNumber()))
                : InventorySelectionDTO.standing(1);

            reservationService.removeLine(credential, line.eventId(), line.zoneId(), selection);
            ActiveOrderDTO updated = reservationService.viewMyActiveOrder(credential);
            return new RemoveOutcome.Removed(updated);
        } catch (RuntimeException e) {
            return new RemoveOutcome.Failure(e.getMessage());
        }
    }

    public sealed interface LoadOutcome {
        record Shown(ActiveOrderDTO order) implements LoadOutcome { }
        record Empty()                     implements LoadOutcome { }
        record NotAuthenticated()          implements LoadOutcome { }
        record Failure(String reason)      implements LoadOutcome { }
    }

    public sealed interface RemoveOutcome {
        record Removed(ActiveOrderDTO order) implements RemoveOutcome { }
        record Failure(String reason)        implements RemoveOutcome { }
    }
}