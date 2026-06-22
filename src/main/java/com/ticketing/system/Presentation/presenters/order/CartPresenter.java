package com.ticketing.system.Presentation.presenters.order;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.ActiveOrderDTO;
import com.ticketing.system.Core.Application.dto.InventorySelectionDTO;
import com.ticketing.system.Core.Application.services.ReservationService;
import com.ticketing.system.Presentation.components.Money;

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

    public LoadOutcome loadCart(String credential) {
        if (credential == null || credential.isBlank()) {
            return new LoadOutcome.NotAuthenticated();
        }
        try {
            ActiveOrderDTO order = reservationService.viewMyActiveOrder(credential);
            if (order == null || order.lines().isEmpty()) {
                return new LoadOutcome.Empty();
            }
            return new LoadOutcome.Shown(order, subtotalCents(order));
        } catch (RuntimeException e) {
            return new LoadOutcome.Failure(e.getMessage());
        }
    }

    public RemoveOutcome removeLine(String credential, ActiveOrderDTO.CartLineDTO line) {
        try {
            InventorySelectionDTO selection = (line.seatNumber() != null)
                ? InventorySelectionDTO.seated(List.of(line.seatNumber()))
                : InventorySelectionDTO.standing(1);

            reservationService.removeLine(credential, line.eventId(), line.zoneId(), selection);
            ActiveOrderDTO updated = reservationService.viewMyActiveOrder(credential);
            return new RemoveOutcome.Removed(updated, subtotalCents(updated));
        } catch (RuntimeException e) {
            return new RemoveOutcome.Failure(e.getMessage());
        }
    }

    private long subtotalCents(ActiveOrderDTO order) {
        if (order == null || order.lines().isEmpty()) return 0L;
        return order.lines().stream()
                .mapToLong(l -> Money.toCents(l.pricePerTicket()))
                .sum();
    }

    public sealed interface LoadOutcome {
        record Shown(ActiveOrderDTO order, long subtotalCents) implements LoadOutcome { }
        record Empty()                     implements LoadOutcome { }
        record NotAuthenticated()          implements LoadOutcome { }
        record Failure(String reason)      implements LoadOutcome { }
    }

    public sealed interface RemoveOutcome {
        record Removed(ActiveOrderDTO order, long subtotalCents) implements RemoveOutcome { }
        record Failure(String reason)        implements RemoveOutcome { }
    }
}