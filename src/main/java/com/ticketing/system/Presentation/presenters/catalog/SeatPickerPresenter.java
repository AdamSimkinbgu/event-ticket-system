package com.ticketing.system.Presentation.presenters.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.InventorySelectionDTO;
import com.ticketing.system.Core.Application.dto.InventoryZoneDTO;
import com.ticketing.system.Core.Application.dto.SeatDTO;
import com.ticketing.system.Core.Application.dto.VenueMapDTO;
import com.ticketing.system.Core.Application.services.CatalogService;
import com.ticketing.system.Core.Application.services.ReservationService;
import com.ticketing.system.Core.Domain.events.ZoneType;
import com.ticketing.system.Presentation.components.venue.VkSeat;
import com.ticketing.system.Presentation.components.venue.VkSeatedZonePicker;
import com.ticketing.system.Presentation.session.SessionIdentity;

/**
 * MVP presenter for the seat/zone picker. Loads a zone from {@link CatalogService},
 * reserves seated or standing inventory via {@link ReservationService} for the
 * current {@link SessionIdentity}, and exposes a static helper that lays seats out
 * into row/column seat models for the venue widget. Returns sealed {@code Outcome}
 * results the view renders.
 */
@Component
public class SeatPickerPresenter {

    private final CatalogService catalogService;
    private final ReservationService reservationService;
    private final SessionIdentity identity;

    @Autowired
    public SeatPickerPresenter(CatalogService catalogService,
                               ReservationService reservationService,
                               SessionIdentity identity) {
        this.catalogService = catalogService;
        this.reservationService = reservationService;
        this.identity = identity;
    }

    /**
     * Loads a single zone of an event's venue map for display.
     *
     * @param eventId the event id
     * @param zoneId  the zone id within the event
     * @return {@link LoadOutcome.Loaded} with the zone and its type,
     *         {@link LoadOutcome.NotFound} if the zone is gone, or
     *         {@link LoadOutcome.Failure} on error
     */
    public LoadOutcome loadZone(int eventId, int zoneId) {
        try {
            VenueMapDTO map = catalogService.getEventVenueMap(identity.credential(), eventId);
            if (map == null) {
                return new LoadOutcome.Failure("Could not load venue map");
            }
            InventoryZoneDTO zone = map.inventoryZones().stream()
                    .filter(z -> z.getId() == zoneId)
                    .findFirst()
                    .orElse(null);
            if (zone == null) {
                return new LoadOutcome.NotFound("That zone is no longer available");
            }
            ZoneType zoneType = ZoneType.valueOf(zone.getZoneType());
            return new LoadOutcome.Loaded(zone, zoneType);
        } catch (RuntimeException e) {
            return new LoadOutcome.Failure("Could not load this zone — please refresh and try again");
        }
    }

    /**
     * Reserves specific seats, dispatching to the member or guest reservation path
     * by the current identity.
     *
     * @param selection the seated selection (carries the seat numbers)
     * @param eventId   the event id
     * @param zoneId    the zone id
     * @return {@link ReserveOutcome.Success} with the seat count, or
     *         {@link ReserveOutcome.Failure} on error
     */
    public ReserveOutcome reserveSeated(InventorySelectionDTO selection, int eventId, int zoneId) {
        try {
            if (identity.isMember()) {
                reservationService.reserveForMember(identity.memberToken(), eventId, zoneId, selection);
            } else {
                reservationService.reserveForGuest(identity.guestSessionId(), eventId, zoneId, selection);
            }
            return new ReserveOutcome.Success(selection.getSeatNumbers().size());
        } catch (RuntimeException e) {
            return new ReserveOutcome.Failure(e.getMessage());
        }
    }

    /**
     * Reserves a quantity of standing-room inventory, dispatching to the member or
     * guest reservation path by the current identity.
     *
     * @param quantity the number of standing tickets to reserve
     * @param eventId  the event id
     * @param zoneId   the zone id
     * @return {@link ReserveOutcome.Success} with the quantity, or
     *         {@link ReserveOutcome.Failure} on error
     */
    public ReserveOutcome reserveStanding(int quantity, int eventId, int zoneId) {
        try {
            InventorySelectionDTO selection = InventorySelectionDTO.standing(quantity);
            if (identity.isMember()) {
                reservationService.reserveForMember(identity.memberToken(), eventId, zoneId, selection);
            } else {
                reservationService.reserveForGuest(identity.guestSessionId(), eventId, zoneId, selection);
            }
            return new ReserveOutcome.Success(quantity);
        } catch (RuntimeException e) {
            return new ReserveOutcome.Failure(e.getMessage());
        }
    }

    /**
     * Lays a zone's seats out into row/column seat models for the venue widget,
     * grouping by row label and ordering by seat number within each row.
     *
     * @param zone           the seated zone
     * @param seatStepPixels the pixel step between adjacent seats/rows
     * @return the positioned seat models, with per-seat availability state
     */
    public static List<VkSeatedZonePicker.SeatModel> buildSeatModels(InventoryZoneDTO zone, int seatStepPixels) {
        TreeMap<String, List<SeatDTO>> byRow = new TreeMap<>();
        for (SeatDTO s : zone.getSeats()) {
            byRow.computeIfAbsent(rowOf(s.label()), k -> new ArrayList<>()).add(s);
        }
        List<VkSeatedZonePicker.SeatModel> models = new ArrayList<>();
        int rowIdx = 0;
        for (List<SeatDTO> rowSeats : byRow.values()) {
            rowSeats.sort(Comparator.comparingInt(s -> seatNumber(s.label())));
            int colIdx = 0;
            for (SeatDTO s : rowSeats) {
                models.add(new VkSeatedZonePicker.SeatModel(
                        s.label(), colIdx * seatStepPixels, rowIdx * seatStepPixels, stateFor(s.status())));
                colIdx++;
            }
            rowIdx++;
        }
        return models;
    }

    /**
     * @param status the seat status string from the DTO
     * @return the widget seat state (free/sold/held)
     */
    private static VkSeat.State stateFor(String status) {
        return switch (status) {
            case "AVAILABLE" -> VkSeat.State.free;
            case "SOLD"      -> VkSeat.State.sold;
            default          -> VkSeat.State.held;
        };
    }

    /**
     * @param label a seat label (e.g. "A12")
     * @return the leading non-digit run (the row), or the whole label if none
     */
    private static String rowOf(String label) {
        int i = 0;
        while (i < label.length() && !Character.isDigit(label.charAt(i))) {
            i++;
        }
        return i > 0 ? label.substring(0, i) : label;
    }

    /**
     * @param label a seat label (e.g. "A12")
     * @return the trailing numeric part (the seat number), or 0 if unparseable
     */
    private static int seatNumber(String label) {
        int i = 0;
        while (i < label.length() && !Character.isDigit(label.charAt(i))) {
            i++;
        }
        try {
            return Integer.parseInt(label.substring(i));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Result of {@link #loadZone(int, int)}. */
    public sealed interface LoadOutcome {
        /** The zone loaded: render the picker for its type. */
        record Loaded(InventoryZoneDTO zone, ZoneType zoneType) implements LoadOutcome { }
        /** The zone no longer exists. */
        record NotFound(String message) implements LoadOutcome { }
        /** Loading failed: show the message. */
        record Failure(String message)  implements LoadOutcome { }
    }

    /** Result of a reserve attempt. */
    public sealed interface ReserveOutcome {
        /** Reservation succeeded for the given quantity. */
        record Success(int quantity)   implements ReserveOutcome { }
        /** Reservation failed: show the message. */
        record Failure(String message) implements ReserveOutcome { }
    }
}