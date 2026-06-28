package com.ticketing.system.Presentation.presenters.company;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.GridPlacementDTO;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Application.dto.VenueLayoutDTO;
import com.ticketing.system.Core.Application.dto.VenueMapConfigDTO;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Application.services.EventManagementService;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;

/**
 * MVP presenter for the venue-map editor (UC-20). Loads an event's zone layout,
 * saves a new venue-map configuration via {@link EventManagementService}, and
 * validates a single zone's form input before it is added. Returns sealed
 * outcomes the view renders. Holds no Vaadin imports.
 */
@Component
public class VenueMapPresenter {

    private final EventManagementService eventService;
    private final CompanyManagementService companyManagementService;

    @Autowired
    public VenueMapPresenter(EventManagementService eventService,
                             CompanyManagementService companyManagementService) {
        this.eventService = eventService;
        this.companyManagementService = companyManagementService;
    }

    /**
     * Loads an event's current zone layout for the editor.
     *
     * @param token   the owner's token
     * @param eventId the event whose layout to load
     * @return {@link LoadOutcome.Success} with the layout,
     *         {@link LoadOutcome.NotAuthenticated}, or {@link LoadOutcome.Failure}
     */
    public LoadOutcome loadZones(String token, int eventId) {
        if (token == null) return new LoadOutcome.NotAuthenticated();
        try {
            VenueLayoutDTO layout = eventService.getEventZones(token, eventId);
            return new LoadOutcome.Success(layout);
        } catch (InvalidTokenException e) {
            return new LoadOutcome.NotAuthenticated();
        } catch (RuntimeException e) {
            return new LoadOutcome.Failure(e.getMessage());
        }
    }

    /**
     * Saves a venue-map configuration for the caller's first owned company.
     *
     * @param token   the owner's token
     * @param eventId the event id (carried in {@code config}; kept for the view's call shape)
     * @param config  the venue-map configuration to apply
     * @return {@link SaveOutcome.Success}; or {@link SaveOutcome.NotAuthenticated},
     *         {@link SaveOutcome.NoCompany}, or {@link SaveOutcome.Failure}
     */
    public SaveOutcome saveMap(String token, String eventId, VenueMapConfigDTO config) {
        if (token == null) return new SaveOutcome.NotAuthenticated();
        try {
            List<ProductionCompanyDTO> owned = companyManagementService.findOwnedCompanies(token);
            if (owned.isEmpty()) return new SaveOutcome.NoCompany();
            int companyId = owned.get(0).companyId();
            eventService.configureVenueMap(token, companyId, config);
            return new SaveOutcome.Success();
        } catch (InvalidTokenException e) {
            return new SaveOutcome.NotAuthenticated();
        } catch (RuntimeException e) {
            return new SaveOutcome.Failure(e.getMessage());
        }
    }

    /**
     * Validates and normalizes one zone's form input before it is added to the map.
     *
     * @param name             the zone name
     * @param priceText        the per-ticket price as entered
     * @param seated           whether the zone is seated
     * @param rows             seated rows (defaults to 1 if null)
     * @param seatsPerRow      seated seats per row (defaults to 1 if null)
     * @param standingCapacity standing capacity (defaults to 1 if null; ignored when seated)
     * @param placement        the grid placement, or {@code null}
     * @return {@link ZoneOutcome.Valid} with normalized values, or
     *         {@link ZoneOutcome.InvalidName} / {@link ZoneOutcome.InvalidPrice}
     */
    public ZoneOutcome validateZone(String name, String priceText, boolean seated,
                                    Integer rows, Integer seatsPerRow, Integer standingCapacity,
                                    GridPlacementDTO placement) {
        if (name == null || name.isBlank()) return new ZoneOutcome.InvalidName();
        double price;
        try { price = Double.parseDouble(priceText == null ? "" : priceText.trim()); }
        catch (NumberFormatException e) { return new ZoneOutcome.InvalidPrice(); }
        int r   = rows == null ? 1 : rows;
        int spr = seatsPerRow == null ? 1 : seatsPerRow;
        int cap = seated ? 0 : (standingCapacity == null ? 1 : standingCapacity);
        return new ZoneOutcome.Valid(name, seated, r, spr, cap, price, placement);
    }

    /** Result of {@link #validateZone}. */
    public sealed interface ZoneOutcome {
        record Valid(String name, boolean seated, int rows, int seatsPerRow,
                    int capacity, double price, GridPlacementDTO placement) implements ZoneOutcome {}
        record InvalidName()  implements ZoneOutcome {}
        record InvalidPrice() implements ZoneOutcome {}
    }

    /** Result of {@link #loadZones(String, int)}. */
    public sealed interface LoadOutcome {
        record Success(VenueLayoutDTO layout) implements LoadOutcome {}
        record NotAuthenticated() implements LoadOutcome {}
        record Failure(String reason) implements LoadOutcome {}
    }

    /** Result of {@link #saveMap(String, String, VenueMapConfigDTO)}. */
    public sealed interface SaveOutcome {
        record Success() implements SaveOutcome {}
        record NotAuthenticated() implements SaveOutcome {}
        record NoCompany() implements SaveOutcome {}
        record Failure(String reason) implements SaveOutcome {}
    }
}