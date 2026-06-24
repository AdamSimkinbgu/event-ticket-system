package com.ticketing.system.Infrastructure.dev.seed;

import com.ticketing.system.Core.Application.dto.CardDetailsDTO;
import com.ticketing.system.Core.Application.dto.EventDetailDTO;
import com.ticketing.system.Core.Application.dto.InventorySelectionDTO;
import com.ticketing.system.Core.Application.services.CheckoutService;
import com.ticketing.system.Core.Application.services.ReservationService;

import java.util.Map;

/**
 * Seeds the buyer-side activity: each of the four named buyers gets
 * two past purchases (real reserve + checkout flow via
 * {@link ReservationService} + {@link CheckoutService}) and one active
 * reservation that stays in their cart on first load. Inventory zone
 * counters and ticket aggregates are updated through the real services,
 * so the resulting state passes every domain invariant.
 *
 * <p>Reservations target standing zones only (sports + theatre events
 * are all seated and would require knowing seat labels per zone). The
 * standing-zone-bearing events are the four music shows + the comedy
 * showcase — selections are spread across these so demo dashboards
 * have varied per-event sales.
 *
 * <p>In the {@code dev} profile these run through the real
 * {@link com.ticketing.system.Infrastructure.external.WsepPaymentGateway} /
 * {@link com.ticketing.system.Infrastructure.external.WsepTicketIssuer}, so the
 * seeded tickets carry real WSEP barcodes (boot depends on the endpoint being up).
 */
public final class DemoOrders {

    private final ReservationService reservationService;
    private final CheckoutService checkoutService;
    private final Map<String, SeededUser> users;
    private final Map<String, EventDetailDTO> events;

    public DemoOrders(ReservationService reservationService,
                      CheckoutService checkoutService,
                      Map<String, SeededUser> users,
                      Map<String, EventDetailDTO> events) {
        this.reservationService = reservationService;
        this.checkoutService = checkoutService;
        this.users = users;
        this.events = events;
    }

    public void seed() {
        // -- Avi: 2 past + 1 active --
        reserveAndCheckout(DemoUsers.AVI_BUYER,
            "Coldplay — Music of the Spheres", /* GA */ 3, 2);
        reserveAndCheckout(DemoUsers.AVI_BUYER,
            "Mediterranean Music Festival",     /* Festival */ 2, 3);
        reservePending(DemoUsers.AVI_BUYER,
            "Beyoncé — Renaissance",            /* GA */ 2, 2);

        // -- Dana: 2 past + 1 active --
        reserveAndCheckout(DemoUsers.DANA_BUYER,
            "Imagine Dragons — Loom",           /* GA */ 3, 1);
        reserveAndCheckout(DemoUsers.DANA_BUYER,
            "Beyoncé — Renaissance",            /* Front pit */ 1, 2);
        reservePending(DemoUsers.DANA_BUYER,
            "Mediterranean Music Festival",      /* VIP */ 1, 1);

        // -- Ido: 2 past + 1 active --
        reserveAndCheckout(DemoUsers.IDO_BUYER,
            "Beyoncé — Renaissance",            /* GA */ 2, 2);
        reserveAndCheckout(DemoUsers.IDO_BUYER,
            "Coldplay — Music of the Spheres", /* GA */ 3, 1);
        reservePending(DemoUsers.IDO_BUYER,
            "Stand-Up Showcase — 21+",         /* Bar standing */ 2, 2);

        // -- Maya: 2 past + 1 active --
        reserveAndCheckout(DemoUsers.MAYA_BUYER,
            "Mediterranean Music Festival",     /* Festival */ 2, 4);
        reserveAndCheckout(DemoUsers.MAYA_BUYER,
            "Imagine Dragons — Loom",           /* GA */ 3, 2);
        reservePending(DemoUsers.MAYA_BUYER,
            "Coldplay — Music of the Spheres", /* GA */ 3, 2);
    }

    private void reserveAndCheckout(String buyerKey, String eventName, int zoneId, int quantity) {
        EventDetailDTO event = events.get(eventName);
        String token = users.get(buyerKey).token();
        int eventId = Integer.parseInt(event.eventId());

        reservationService.reserveForMember(token, eventId, zoneId,
            InventorySelectionDTO.standing(quantity));

        String idem = "demo-" + buyerKey + "-" + eventName.hashCode();
        checkoutService.checkoutMember(token, idem, "ILS",
            new CardDetailsDTO("4111111111111111", "123", 12, 2030, "Demo " + buyerKey));
    }

    private void reservePending(String buyerKey, String eventName, int zoneId, int quantity) {
        EventDetailDTO event = events.get(eventName);
        String token = users.get(buyerKey).token();
        int eventId = Integer.parseInt(event.eventId());

        reservationService.reserveForMember(token, eventId, zoneId,
            InventorySelectionDTO.standing(quantity));
    }
}
