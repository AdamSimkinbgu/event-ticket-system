package com.ticketing.system.Core.Application.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.system.Core.Application.dto.RefundResultDTO;
import com.ticketing.system.Core.Domain.exceptions.BusinessRuleViolationException;
import com.ticketing.system.Core.Domain.exceptions.CompanyNotFoundException;
import com.ticketing.system.Core.Domain.exceptions.EventNotFoundException;
import com.ticketing.system.Core.Domain.exceptions.InvalidStateTransitionException;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Core.Domain.exceptions.UserNotFoundException;
import com.ticketing.system.Core.Domain.exceptions.RefundFailedException;
import com.ticketing.system.Core.Domain.orders.TransactionRecord;
import com.ticketing.system.Core.Application.dto.EventCreationDTO;
import com.ticketing.system.Core.Application.dto.EventDetailDTO;
import com.ticketing.system.Core.Application.dto.EventPolicyConfigDTO;
import com.ticketing.system.Core.Application.dto.EventUpdateDTO;
import com.ticketing.system.Core.Application.dto.PurchasePolicyDTO;
import com.ticketing.system.Core.Application.dto.GridPlacementDTO;
import com.ticketing.system.Core.Application.dto.VenueLayoutDTO;
import com.ticketing.system.Core.Application.dtoMappers.EventMapper;
import com.ticketing.system.Core.Application.dto.VenueMapConfigDTO;
import com.ticketing.system.Core.Application.dto.ZoneDetailDTO;
import com.ticketing.system.Core.Application.interfaces.IPaymentGateway;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.interfaces.INotificationService;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.Tickets.TicketStatus;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Domain.events.EventCategory;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.Location;
import com.ticketing.system.Core.Domain.events.InventoryZone;
import com.ticketing.system.Core.Domain.events.StandingZone;
import com.ticketing.system.Core.Domain.events.VenueMap;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;
import com.ticketing.system.Core.Domain.orders.ReceiptLine;
import com.ticketing.system.Core.Domain.events.DiscountPolicy;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.users.User;
import com.ticketing.system.Core.Domain.users.Permission;
import com.ticketing.system.Core.Domain.events.Seat;
import com.ticketing.system.Core.Domain.events.SeatedZone;
import com.ticketing.system.Core.Domain.events.ShowDate;
import com.ticketing.system.Core.Domain.policies.purchase.NoPurchasePolicy;
import com.ticketing.system.Core.Domain.policies.purchase.OrPurchasePolicy;
import com.ticketing.system.Core.Domain.policies.purchase.PurchasePolicy;
import com.ticketing.system.Core.Domain.policies.purchase.AgePurchasePolicy;
import com.ticketing.system.Core.Domain.policies.purchase.AndPurchasePolicy;
import com.ticketing.system.Core.Domain.policies.purchase.MaxTicketsPurchasePolicy;
import com.ticketing.system.Core.Domain.policies.purchase.MinTicketsPurchasePolicy;

/**
 * Owner / Manager-side write service for the Event aggregate and its lifecycle.
 * UC-19 (Manage Event Catalog), UC-20 (Configure Venue Map &amp; Inventory),
 * UC-21 (Configure Policies).
 *
 * <p>Events are created in DRAFT (see {@link #addEvent}); the venue map and
 * inventory zones are configured separately (UC-20) before the event goes live,
 * which keeps each step's validation focused. Lifecycle mutations take a
 * per-event write lock via {@code eventRepository.lockForUpdate} so they can't
 * race buyer reservations. Authorization is a domain concern: the caller is
 * resolved from the token and the permission/ownership check is delegated to the
 * {@code User} / {@code ProductionCompany} aggregates.
 */
@Service
@Slf4j
public class EventManagementService {

    private final IEventRepository eventRepository;
    private final IProductionCompanyRepository companyRepository;
    private final ITicketRepository ticketRepository;
    private final ISessionManager sessionManager;
    private final IOrderReceiptRepository orderReceiptRepository;
    private final IPaymentGateway paymentGateway;
    private final IUserRepository userRepository;
    private final INotificationService notificationService;
    private int currentVenueMapIdCounter;

    public EventManagementService(
            IEventRepository eventRepository,
            IProductionCompanyRepository companyRepository,
            ITicketRepository ticketRepository,
            ISessionManager sessionManager,
            IOrderReceiptRepository orderReceiptRepository,
            IPaymentGateway paymentGateway,
            IUserRepository userRepository,
            INotificationService notificationService) {
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.ticketRepository = ticketRepository;
        this.sessionManager = sessionManager;
        this.orderReceiptRepository = orderReceiptRepository;
        this.paymentGateway = paymentGateway;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.currentVenueMapIdCounter = 0; // Initialize the venue map ID counter, change the counter to be internal but
                                           // here for now.
    }

    /**
     * UC-19 — adds an Event in DRAFT state. The venue map and inventory zones are
     * configured later via {@link #configureVenueMap} (UC-20), keeping creation
     * and venue setup as separate, focused steps.
     *
     * <p>The event's effective purchase policy is the company's policy ANDed with
     * the event-specific policy built from the request. Discounts are not yet in
     * the implementation plan, so a 0% {@link DiscountPolicy} is applied.
     *
     * @param token   the caller's token
     * @param request the new event's details, including its company and optional
     *                event-specific purchase policy
     * @return the created event's detail view
     * @throws InvalidTokenException       if the token is invalid
     * @throws RuntimeException            if the company does not exist
     * @throws UnauthorizedActionException if the caller lacks {@code CONFIGURE_VENUE}
     */
    @Transactional
    public EventDetailDTO addEvent(String token, EventCreationDTO request) {
        int ownerId = validateTokenAndGetUserId(token);

        ProductionCompany company = null;
        // TODO: see about all the other exceptions throws handlings/catches.
        try {
            company = companyRepository.getCompanyById(request.companyId());
        } catch (RuntimeException e) {
            log.error("Error - company not found: {}, {}", request.companyId(), e.getMessage());
            throw new RuntimeException("Company not found");
        }

        User user = userRepository.getUserById(ownerId);
        user.requirePermissionInCompany(request.companyId(), Permission.CONFIGURE_VENUE);

        int newEventId = eventRepository.nextId();
        VenueMap venueMap = new VenueMap(eventRepository.nextVenueMapId(), request.location(), List.of());
        // ! Note: Discount policy is currently not in the implementation plan so just
        // put as 0 discount for every event here
        // not doing discount automatically without the ability to change this from the
        // outside right now.
        DiscountPolicy discountPolicy = new DiscountPolicy(0);

        PurchasePolicy companyPurchasePolicy = company.getPurchasePolicy();
        if (companyPurchasePolicy == null) {
            companyPurchasePolicy = new NoPurchasePolicy();
        }

        PurchasePolicy eventSpecificPurchasePolicy = buildPurchasePolicyFromDTO(request.purchasePolicy());

        PurchasePolicy inheritedAndExtendedPurchasePolicy = new AndPurchasePolicy(
                companyPurchasePolicy,
                eventSpecificPurchasePolicy);

        Event newEvent = new Event(
                newEventId,
                request.name(),
                request.description(),
                request.rating(),
                request.artistsNames(),
                request.category(),
                request.companyId(),
                EventStatus.DRAFT,
                venueMap,
                request.showDates(),
                inheritedAndExtendedPurchasePolicy,
                discountPolicy);
        eventRepository.save(newEvent);

        log.info("Event {} created successfully with ID {}", request.name(), newEventId);
        return new EventMapper().toEventDetailDTO(newEvent, company.getName());
    }

    /**
     * II.4.1.1 — lists all events under a company (owner/manager view).
     *
     * @param token     the caller's token
     * @param companyId the company whose events to list
     * @return the company's events as detail views
     * @throws InvalidTokenException       if the token is invalid
     * @throws UserNotFoundException       if the caller does not exist
     * @throws CompanyNotFoundException    if the company does not exist
     * @throws UnauthorizedActionException if the caller lacks {@code MANAGE_INVENTORY}
     */
    @Transactional(readOnly = true)
    public List<EventDetailDTO> listEventsForCompany(String token, int companyId) {
        int userId = validateTokenAndGetUserId(token);
        User user = userRepository.getUserById(userId);
        if (user == null) {
            throw new UserNotFoundException(userId);
        }

        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null) {
            throw new CompanyNotFoundException("Company not found for ID: " + companyId);
        }

        user.requirePermissionInCompany(companyId, Permission.MANAGE_INVENTORY);
        

        EventMapper mapper = new EventMapper();
        return eventRepository.findByCompanyId(companyId).stream()
            .map(e -> mapper.toEventDetailDTO(e, company.getName()))
            .toList();
    }

    /**
     * Owner/manager read of a single event by id (requires {@code MANAGE_INVENTORY}).
     *
     * @param token   the caller's token
     * @param eventId the event to fetch
     * @return the event's detail view
     * @throws InvalidTokenException       if the token is invalid
     * @throws EventNotFoundException      if the event does not exist
     * @throws UserNotFoundException       if the caller does not exist
     * @throws CompanyNotFoundException    if the owning company does not exist
     * @throws UnauthorizedActionException if the caller lacks {@code MANAGE_INVENTORY}
     */
    @Transactional(readOnly = true)
    public EventDetailDTO getEvent(String token, int eventId) {
        int userId = validateTokenAndGetUserId(token);
        Event event = eventRepository.findById(eventId);
        if (event == null) {
            throw new EventNotFoundException(eventId);
        }
        User user = userRepository.getUserById(userId);
        if (user == null) {
            throw new UserNotFoundException(userId);
        }
        user.requirePermissionInCompany(event.getCompanyId(), Permission.MANAGE_INVENTORY);
        ProductionCompany company = companyRepository.getCompanyById(event.getCompanyId());
        if (company == null) {
            throw new CompanyNotFoundException("Company not found for ID: " + event.getCompanyId());
        }

        return new EventDetailDTO(
            String.valueOf(event.getId()),
            event.getName(),
            event.getRating(),
            null,
            event.getCategory(),
            event.getVenueMap() != null ? event.getVenueMap().getLocation() : null,
            String.valueOf(event.getCompanyId()),
            company.getName(),
            event.getStatus(),
            event.getShowDates(),
            event.getArtistsNames()
            );
    }

    



    /**
     * II.4.2.3 — reads back the current zone states from the domain so the editor
     * reflects real-time inventory (capacity consumed by sales, etc.). For seated
     * zones the row/seats-per-row counts are derived from the seat labels (the
     * leading non-digit run is the row, so multi-char rows and &gt;26 rows are
     * handled correctly).
     *
     * @param token   the caller's token
     * @param eventId the event whose zone layout to read
     * @return the venue layout (grid dimensions + per-zone details)
     * @throws InvalidTokenException       if the token is invalid
     * @throws UserNotFoundException       if the caller does not exist
     * @throws EventNotFoundException      if the event does not exist
     * @throws UnauthorizedActionException if the caller lacks {@code CONFIGURE_VENUE}
     */
    @Transactional(readOnly = true)
    public VenueLayoutDTO getEventZones(String token, int eventId) {
        int userId = validateTokenAndGetUserId(token);
        User user = userRepository.getUserById(userId);
        if (user == null) {
            throw new UserNotFoundException(userId);
        }
        Event event = eventRepository.findById(eventId);
        if (event == null) {
            throw new EventNotFoundException("Event not found for ID: " + eventId);
        }
        user.requirePermissionInCompany(event.getCompanyId(), Permission.CONFIGURE_VENUE);

        VenueMap map = event.getVenueMap();
        if (map == null) {
            return new VenueLayoutDTO(VenueMap.DEFAULT_GRID_ROWS, VenueMap.DEFAULT_GRID_COLS, List.of());
        }
        if (map.getInventoryZones().isEmpty()) {
            return new VenueLayoutDTO(map.getGridRows(), map.getGridCols(), List.of());
        }

        List<ZoneDetailDTO> result = new ArrayList<>();
        for (InventoryZone zone : map.getInventoryZones()) {
            GridPlacementDTO placement = zone.hasGridPlacement()
                    ? new GridPlacementDTO(zone.getGridRow(), zone.getGridCol(),
                            zone.getGridRowSpan(), zone.getGridColSpan())
                    : null;
            if (zone.isSeated()) {
                SeatedZone sz = (SeatedZone) zone;
                List<Seat> seats = sz.getSeats();
                // Seat labels are "<rowLabel><number>" where rowLabel may be multi-char
                // (e.g. "AA12"). Group by the leading non-digit run so the row/seat-per-row
                // counts stay accurate for multi-char rows and >26 rows — not just charAt(0).
                Map<String, Integer> seatsByRow = new LinkedHashMap<>();
                for (Seat s : seats) {
                    String label = s.getLabel();
                    int i = 0;
                    while (i < label.length() && !Character.isDigit(label.charAt(i)))
                        i++;
                    String rowLabel = i > 0 ? label.substring(0, i) : label;
                    seatsByRow.merge(rowLabel, 1, Integer::sum);
                }
                int rows = seatsByRow.size();
                int seatsPerRow = seatsByRow.values().stream().mapToInt(Integer::intValue).max().orElse(0);
                result.add(new ZoneDetailDTO(zone.getName(), true, rows, seatsPerRow, 0, zone.getprice(), placement));
            } else {
                result.add(new ZoneDetailDTO(
                        zone.getName(), false, 0, 0, zone.getCapacity(), zone.getprice(), placement));
            }
        }
        return new VenueLayoutDTO(map.getGridRows(), map.getGridCols(), result);
    }

    /**
     * UC-20 — (re)configures the venue map and inventory zones for an event. May
     * be called multiple times <em>before</em> the event goes live, supporting an
     * iterative setup. Zone ids are assigned 1..N in config order and each zone's
     * optional grid placement is then applied (bounds/overlap validated by
     * {@code VenueMap.placeZoneOnGrid}). Holds the per-event write lock.
     *
     * @param token     the caller's token
     * @param companyId the company the event must belong to
     * @param config    the venue-map configuration (event id, grid size, zones)
     * @throws InvalidTokenException       if the token is invalid
     * @throws UnauthorizedActionException if the caller lacks {@code CONFIGURE_VENUE}
     * @throws IllegalArgumentException    if a standing zone's capacity is not positive
     * @throws RuntimeException            if the event does not belong to the company
     */
    @Transactional
    public void configureVenueMap(String token, int companyId, VenueMapConfigDTO config) {
        int eventId = Integer.parseInt(config.eventId());

        eventRepository.lockForUpdate(eventId);
        try {
            int userId = validateTokenAndGetUserId(token);

            log.info("Configuring venue map for company {}, event {}, by user {}", companyId, config.eventId(), userId);

            User user = userRepository.getUserById(userId);
            user.requirePermissionInCompany(companyId, Permission.CONFIGURE_VENUE);

            Event event = eventRepository.findById(eventId);

            if (event.getCompanyId() != companyId) {
                throw new RuntimeException("Event does not belong to this company");
            }

            List<InventoryZone> zones = new ArrayList<>();
            int nextZoneId = 1;

            for (VenueMapConfigDTO.ZoneConfigDTO zoneConfig : config.zones()) {
                if (zoneConfig.seated()) {
                    List<Seat> seats = toDomainSeats(zoneConfig.seats());
                    zones.add(new SeatedZone(
                            nextZoneId,
                            zoneConfig.zoneName(),
                            zoneConfig.pricePerTicket(),
                            seats));
                } else {
                    if (zoneConfig.capacity() == null || zoneConfig.capacity() <= 0) {
                        throw new IllegalArgumentException("Standing zone capacity must be positive");
                    }

                    zones.add(new StandingZone(
                            nextZoneId,
                            zoneConfig.zoneName(),
                            zoneConfig.capacity(),
                            zoneConfig.pricePerTicket()));
                }

                nextZoneId++;
            }

            int gridRows = config.gridRows() > 0 ? config.gridRows() : VenueMap.DEFAULT_GRID_ROWS;
            int gridCols = config.gridCols() > 0 ? config.gridCols() : VenueMap.DEFAULT_GRID_COLS;
            VenueMap venueMap = new VenueMap(
                    event.getVenueMap() != null ? event.getVenueMap().getId() : eventRepository.nextVenueMapId(),
                    event.getVenueMap() != null ? event.getVenueMap().getLocation() : null,
                    zones, gridRows, gridCols);

            // Apply each zone's grid placement. Zone IDs were assigned 1..N in config
            // order,
            // so the same iteration order maps each config back to its zone. Bounds +
            // overlap
            // are validated by VenueMap.placeZoneOnGrid.
            int placementZoneId = 1;
            for (VenueMapConfigDTO.ZoneConfigDTO zoneConfig : config.zones()) {
                GridPlacementDTO placement = zoneConfig.placement();
                if (placement != null) {
                    venueMap.placeZoneOnGrid(placementZoneId, placement.row(), placement.col(),
                            placement.rowSpan(), placement.colSpan());
                }
                placementZoneId++;
            }

            event.configureVenueMap(venueMap, companyId);
            eventRepository.save(event);

            log.info("Venue map for event {} configured successfully", event.getId());
        } finally {
            eventRepository.unlock(eventId);
        }
    }

    /**
     * UC-19 — partial update of an event's details (name, description, category,
     * location, show dates). Immutability rules (e.g. fields frozen once tickets
     * are sold) are enforced inside {@code Event.editDetails()}. Holds the
     * per-event write lock.
     *
     * @param token  the caller's token
     * @param update the partial update; null fields are left unchanged
     * @throws InvalidTokenException       if the token is invalid
     * @throws IllegalArgumentException    if the event id or category is malformed
     * @throws UnauthorizedActionException if the caller lacks {@code CONFIGURE_VENUE}
     */
    @Transactional
    public void editEventDetails(String token, EventUpdateDTO update) {
        int userId = validateTokenAndGetUserId(token);

        int eventId;
        try {
            eventId = Integer.parseInt(update.eventId());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid event ID: " + update.eventId());
        }

        eventRepository.lockForUpdate(eventId);
        try {
            Event event = eventRepository.findById(eventId);

            User user = userRepository.getUserById(userId);
            user.requirePermissionInCompany(event.getCompanyId(), Permission.CONFIGURE_VENUE);

            EventCategory newCategory = null;
            if (update.category() != null && !update.category().isBlank()) {
                try {
                    newCategory = EventCategory.valueOf(update.category().trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Unknown event category: " + update.category());
                }
            }

            Location newLocation = update.location() == null
                    ? null
                    : new Location(update.location().country(), update.location().city());

            List<ShowDate> newShowDates = update.showDates() == null
                    ? null
                    : update.showDates().stream()
                            .map(sd -> new ShowDate(sd.startsAt(), sd.endsAt()))
                            .toList();

            event.editDetails(update.name(), update.description(), newCategory, newLocation, newShowDates);
            eventRepository.save(event);

            log.info("Event {} details updated by user {}", eventId, userId);
        } finally {
            eventRepository.unlock(eventId);
        }
    }

    /**
     * UC-20 — adds a single inventory zone (seated or standing) to an event,
     * applying its optional grid placement. Holds the per-event write lock.
     *
     * @param token      the caller's token
     * @param companyId  the company the event must belong to
     * @param eventId    the event to add the zone to
     * @param zoneConfig the zone configuration
     * @return the id assigned to the new zone
     * @throws IllegalArgumentException    if the config is null, or a standing zone's capacity is not positive
     * @throws UnauthorizedActionException if the caller lacks {@code CONFIGURE_VENUE}
     * @throws RuntimeException            if the event does not belong to the company
     */
    @Transactional
    public int addInventoryZone(String token, int companyId, int eventId, VenueMapConfigDTO.ZoneConfigDTO zoneConfig) {

        eventRepository.lockForUpdate(eventId);

        try {
            if (zoneConfig == null) {
                throw new IllegalArgumentException("Zone config cannot be null");
            }

            Event event = getAuthorizedEventForVenueEdit(token, companyId, eventId);

            int newZoneId;

            if (zoneConfig.seated()) {
                List<Seat> seats = toDomainSeats(zoneConfig.seats());

                newZoneId = event.addSeatedZone(
                        zoneConfig.zoneName(),
                        zoneConfig.pricePerTicket(),
                        seats,
                        companyId);
            } else {
                if (zoneConfig.capacity() == null || zoneConfig.capacity() <= 0) {
                    throw new IllegalArgumentException("Standing zone capacity must be positive");
                }

                newZoneId = event.addStandingZone(
                        zoneConfig.zoneName(),
                        zoneConfig.capacity(),
                        zoneConfig.pricePerTicket(),
                        companyId);
            }

            if (zoneConfig.placement() != null) {
                GridPlacementDTO p = zoneConfig.placement();
                event.getVenueMap().placeZoneOnGrid(newZoneId, p.row(), p.col(), p.rowSpan(), p.colSpan());
            }

            eventRepository.save(event);

            log.info("Added inventory zone {} to event {} in company {}", newZoneId, eventId, companyId);

            return newZoneId;
        } finally {
            eventRepository.unlock(eventId);
        }
    }

    /**
     * UC-20 — removes an inventory zone from an event. Holds the per-event write
     * lock.
     *
     * @param token     the caller's token
     * @param companyId the company the event must belong to
     * @param eventId   the event to remove the zone from
     * @param zoneId    the zone to remove
     * @throws UnauthorizedActionException if the caller lacks {@code CONFIGURE_VENUE}
     * @throws RuntimeException            if the event does not belong to the company
     */
    @Transactional
    public void removeInventoryZone(String token, int companyId, int eventId, int zoneId) {

        eventRepository.lockForUpdate(eventId);

        try {
            Event event = getAuthorizedEventForVenueEdit(token, companyId, eventId);

            event.removeInventoryZone(zoneId, companyId);

            eventRepository.save(event);

            log.info("Removed inventory zone {} from event {} in company {}", zoneId, eventId, companyId);
        } finally {
            eventRepository.unlock(eventId);
        }
    }

    /**
     * UC-20 — adds capacity (places) to a standing zone. Holds the per-event
     * write lock.
     *
     * @param token       the caller's token
     * @param companyId   the company the event must belong to
     * @param eventId     the event
     * @param zoneId      the standing zone to grow
     * @param placesToAdd the number of places to add
     * @throws UnauthorizedActionException if the caller lacks {@code CONFIGURE_VENUE}
     * @throws RuntimeException            if the event does not belong to the company
     */
    @Transactional
    public void addPlacesToStandingZone(String token, int companyId, int eventId, int zoneId, int placesToAdd) {
        eventRepository.lockForUpdate(eventId);

        try {
            Event event = getAuthorizedEventForVenueEdit(token, companyId, eventId);
            event.addPlacesToStandingZone(zoneId, placesToAdd, companyId);
            eventRepository.save(event);

            log.info("Added {} places to standing zone {} in event {}", placesToAdd, zoneId, eventId);
        } finally {
            eventRepository.unlock(eventId);
        }
    }

    /**
     * UC-20 — removes capacity (places) from a standing zone. Holds the per-event
     * write lock.
     *
     * @param token          the caller's token
     * @param companyId      the company the event must belong to
     * @param eventId        the event
     * @param zoneId         the standing zone to shrink
     * @param placesToRemove the number of places to remove
     * @throws UnauthorizedActionException if the caller lacks {@code CONFIGURE_VENUE}
     * @throws RuntimeException            if the event does not belong to the company
     */
    @Transactional
    public void removePlacesFromStandingZone(String token, int companyId, int eventId, int zoneId, int placesToRemove) {
        eventRepository.lockForUpdate(eventId);

        try {
            Event event = getAuthorizedEventForVenueEdit(token, companyId, eventId);
            event.removePlacesFromStandingZone(zoneId, placesToRemove, companyId);
            eventRepository.save(event);

            log.info("Removed {} places from standing zone {} in event {}", placesToRemove, zoneId, eventId);
        } finally {
            eventRepository.unlock(eventId);
        }
    }

    /**
     * UC-20 — adds specific seats to a seated zone. Holds the per-event write
     * lock.
     *
     * @param token      the caller's token
     * @param companyId  the company the event must belong to
     * @param eventId    the event
     * @param zoneId     the seated zone to add seats to
     * @param seatsToAdd the seats to add
     * @throws UnauthorizedActionException if the caller lacks {@code CONFIGURE_VENUE}
     * @throws RuntimeException            if the event does not belong to the company
     */
    @Transactional
    public void addSeatsToSeatedZone(
            String token,
            int companyId,
            int eventId,
            int zoneId,
            List<VenueMapConfigDTO.SeatConfigDTO> seatsToAdd) {

        eventRepository.lockForUpdate(eventId);

        try {
            Event event = getAuthorizedEventForVenueEdit(token, companyId, eventId);
            event.addSeatsToSeatedZone(zoneId, toDomainSeats(seatsToAdd), companyId);
            eventRepository.save(event);

            log.info("Added {} seats to seated zone {} in event {}", seatsToAdd.size(), zoneId, eventId);
        } finally {
            eventRepository.unlock(eventId);
        }
    }

    /**
     * UC-20 — removes specific seats (by label) from a seated zone. Holds the
     * per-event write lock.
     *
     * @param token              the caller's token
     * @param companyId          the company the event must belong to
     * @param eventId            the event
     * @param zoneId             the seated zone to remove seats from
     * @param seatLabelsToRemove the labels of the seats to remove
     * @throws UnauthorizedActionException if the caller lacks {@code CONFIGURE_VENUE}
     * @throws RuntimeException            if the event does not belong to the company
     */
    @Transactional
    public void removeSeatsFromSeatedZone(
            String token,
            int companyId,
            int eventId,
            int zoneId,
            List<String> seatLabelsToRemove) {

        eventRepository.lockForUpdate(eventId);

        try {
            Event event = getAuthorizedEventForVenueEdit(token, companyId, eventId);
            event.removeSeatsFromSeatedZone(zoneId, seatLabelsToRemove, companyId);
            eventRepository.save(event);

            log.info("Removed {} seats from seated zone {} in event {}", seatLabelsToRemove.size(), zoneId, eventId);
        } finally {
            eventRepository.unlock(eventId);
        }
    }

    /**
     * UC-20 — adds an entire row of seats to a seated zone, generating seat labels
     * and positions from the row label and spacing. Holds the per-event write
     * lock.
     *
     * @param token           the caller's token
     * @param companyId       the company the event must belong to
     * @param eventId         the event
     * @param zoneId          the seated zone to add the row to
     * @param rowLabel        the row's label prefix (e.g. "A")
     * @param firstSeatNumber the first seat number in the row
     * @param numberOfSeats   how many seats to create
     * @param startX          the x-position of the first seat
     * @param y               the row's y-position
     * @param seatSpacing     the horizontal spacing between seats
     * @throws UnauthorizedActionException if the caller lacks {@code CONFIGURE_VENUE}
     * @throws IllegalArgumentException    if any row/seat parameter is invalid
     * @throws RuntimeException            if the event does not belong to the company
     */
    @Transactional
    public void addSeatRowToSeatedZone(
            String token,
            int companyId,
            int eventId,
            int zoneId,
            String rowLabel,
            int firstSeatNumber,
            int numberOfSeats,
            double startX,
            double y,
            double seatSpacing) {

        eventRepository.lockForUpdate(eventId);

        try {
            Event event = getAuthorizedEventForVenueEdit(token, companyId, eventId);

            List<Seat> rowSeats = buildSeatRow(
                    rowLabel,
                    firstSeatNumber,
                    numberOfSeats,
                    startX,
                    y,
                    seatSpacing);
            event.addSeatsToSeatedZone(zoneId, rowSeats, companyId);
            eventRepository.save(event);

            log.info("Added row {} with {} seats to seated zone {} in event {}",
                    rowLabel, numberOfSeats, zoneId, eventId);
        } finally {
            eventRepository.unlock(eventId);
        }
    }

    /**
     * UC-20 — removes an entire row of seats from a seated zone, given the exact
     * seat labels to remove. Holds the per-event write lock.
     *
     * @param token                 the caller's token
     * @param companyId             the company the event must belong to
     * @param eventId               the event
     * @param zoneId                the seated zone to remove the row from
     * @param rowSeatLabelsToRemove the labels of the row's seats to remove
     * @throws UnauthorizedActionException if the caller lacks {@code CONFIGURE_VENUE}
     * @throws RuntimeException            if the event does not belong to the company
     */
    @Transactional
    public void removeSeatRowFromSeatedZone(
            String token,
            int companyId,
            int eventId,
            int zoneId,
            List<String> rowSeatLabelsToRemove) {

        eventRepository.lockForUpdate(eventId);

        try {
            Event event = getAuthorizedEventForVenueEdit(token, companyId, eventId);
            event.removeSeatsFromSeatedZone(zoneId, rowSeatLabelsToRemove, companyId);
            eventRepository.save(event);

            log.info("Removed row seats {} from seated zone {} in event {}",
                    rowSeatLabelsToRemove, zoneId, eventId);
        } finally {
            eventRepository.unlock(eventId);
        }
    }

    /**
     * Validates the token, checks {@code CONFIGURE_VENUE} permission, and loads the
     * event for a venue edit, verifying it belongs to the company. Centralizes the
     * auth/ownership preamble shared by the zone-edit methods.
     *
     * @param token     the caller's token
     * @param companyId the company the event must belong to
     * @param eventId   the event to load
     * @return the authorized event
     * @throws InvalidTokenException       if the token is invalid
     * @throws UnauthorizedActionException if the caller lacks {@code CONFIGURE_VENUE}
     * @throws RuntimeException            if the company/event is missing, or the
     *                                    event does not belong to the company
     */
    private Event getAuthorizedEventForVenueEdit(String token, int companyId, int eventId) {
        int userId = validateTokenAndGetUserId(token);
        User user = userRepository.getUserById(userId);

        try {
            companyRepository.getCompanyById(companyId);
        } catch (RuntimeException e) {
            log.error("Error - company not found: {}, {}", companyId, e.getMessage());
            throw new RuntimeException("Company not found");
        }

        user.requirePermissionInCompany(companyId, Permission.CONFIGURE_VENUE);

        Event event;
        try {
            event = eventRepository.findById(eventId);
        } catch (EventNotFoundException e) {
            log.warn("Event {} not found", eventId);
            throw new RuntimeException("Event not found");
        }

        if (event.getCompanyId() != companyId) {
            throw new RuntimeException("Event does not belong to this company");
        }

        return event;
    }

    /**
     * Converts seat config DTOs to {@code Seat} domain objects.
     *
     * @param seatConfigs the seat configurations
     * @return the corresponding domain seats
     * @throws IllegalArgumentException if the list is null or empty
     */
    private List<Seat> toDomainSeats(List<VenueMapConfigDTO.SeatConfigDTO> seatConfigs) {
        if (seatConfigs == null || seatConfigs.isEmpty()) {
            throw new IllegalArgumentException("Seats list must be non-empty");
        }

        return seatConfigs.stream()
                .map(seatConfig -> new Seat(seatConfig.label(), seatConfig.x(), seatConfig.y()))
                .toList();
    }

    /**
     * Creates a row of {@code Seat} objects, labeling each
     * {@code rowLabel + seatNumber} and positioning them by {@code seatSpacing}.
     *
     * @param rowLabel        the row's label prefix
     * @param firstSeatNumber the first seat number
     * @param numberOfSeats   how many seats to create
     * @param startX          the x-position of the first seat
     * @param y               the row's y-position
     * @param seatSpacing     the horizontal spacing between seats
     * @return the constructed row of seats
     * @throws IllegalArgumentException if any parameter is blank or non-positive
     */
    private List<Seat> buildSeatRow(
            String rowLabel,
            int firstSeatNumber,
            int numberOfSeats,
            double startX,
            double y,
            double seatSpacing) {

        if (rowLabel == null || rowLabel.isBlank()) {
            throw new IllegalArgumentException("Row label must be non-blank");
        }

        if (firstSeatNumber <= 0) {
            throw new IllegalArgumentException("First seat number must be positive");
        }

        if (numberOfSeats <= 0) {
            throw new IllegalArgumentException("Number of seats must be positive");
        }

        if (seatSpacing <= 0) {
            throw new IllegalArgumentException("Seat spacing must be positive");
        }

        List<Seat> rowSeats = new ArrayList<>();

        for (int i = 0; i < numberOfSeats; i++) {
            String seatLabel = rowLabel.trim() + (firstSeatNumber + i);
            rowSeats.add(new Seat(seatLabel, startX + (i * seatSpacing), y));
        }

        return rowSeats;
    }

    /**
     * Detail view for owner-side editing pages (requires {@code CONFIGURE_VENUE}).
     *
     * @param token   the caller's token
     * @param eventId the event to fetch
     * @return the event's detail view
     * @throws InvalidTokenException       if the token is invalid
     * @throws EventNotFoundException      if the event does not exist
     * @throws UnauthorizedActionException if the caller lacks {@code CONFIGURE_VENUE}
     */
    @Transactional(readOnly = true)
    public EventDetailDTO getEventDetail(String token, int eventId) {
        int userId = validateTokenAndGetUserId(token);

        Event event = eventRepository.findById(eventId);

        ProductionCompany company = companyRepository.getCompanyById(event.getCompanyId());

        User user = userRepository.getUserById(userId);
        user.requirePermissionInCompany(event.getCompanyId(), Permission.CONFIGURE_VENUE);

        return new EventMapper().toEventDetailDTO(event, company.getName());
    }

    /**
     * UC-19 — opens an event's sales: SCHEDULED → ON_SALE (idempotent if already
     * ON_SALE). The transition's venue-map/show-date/invariant guards live in
     * {@code Event.transitionToOnSale()}; this method enforces auth, ownership and
     * locking.
     *
     * @param token     the caller's token
     * @param companyId the company the event must belong to
     * @param eventId   the event to publish
     * @throws InvalidTokenException       if the token is invalid
     * @throws UnauthorizedActionException if the caller lacks {@code CONFIGURE_VENUE}
     * @throws RuntimeException            if the event does not belong to the company
     */
    @Transactional
    public void publishEvent(String token, int companyId, int eventId) {
        int userId = validateTokenAndGetUserId(token);

        eventRepository.lockForUpdate(eventId);
        try {
            Event event = eventRepository.findById(eventId);

            if (event.getCompanyId() != companyId) {
                throw new RuntimeException("Event does not belong to this company");
            }

            User user = userRepository.getUserById(userId);
            user.requirePermissionInCompany(companyId, Permission.CONFIGURE_VENUE);

            event.transitionToOnSale(); // SCHEDULED -> ON_SALE (idempotent if already ON_SALE)
            eventRepository.save(event);

            log.info("Event {} published (ON_SALE) by user {}", eventId, userId);
        } finally {
            eventRepository.unlock(eventId);
        }
    }

    /**
     * Permanently removes a CANCELED event. A soft-cancel keeps events for
     * historical/reporting purposes, so deletion is refused if the event still has
     * purchase history (which would orphan its OrderReceipt/Ticket records,
     * referenced by eventId with no cascade). Holds the per-event write lock.
     *
     * @param token   the caller's token
     * @param eventId the event to delete
     * @throws InvalidTokenException          if the token is invalid
     * @throws UnauthorizedActionException    if the caller lacks {@code CONFIGURE_VENUE}
     * @throws InvalidStateTransitionException if the event is not CANCELED
     * @throws BusinessRuleViolationException  if the event has purchase history
     */
    public void deleteEvent(String token, int eventId) {
        int userId = validateTokenAndGetUserId(token);
        eventRepository.lockForUpdate(eventId);
        try {
            Event event = eventRepository.findById(eventId);
            User user = userRepository.getUserById(userId);
            user.requirePermissionInCompany(event.getCompanyId(), Permission.CONFIGURE_VENUE);
            if (event.getStatus() != EventStatus.CANCELED) {
                throw new InvalidStateTransitionException("Only CANCELED events can be deleted");
            }
            // Soft-cancel keeps events "for historical and reporting purposes": refuse to
            // permanently delete one that still has purchase history, which would orphan its
            // OrderReceipt/Ticket records (referenced by eventId, no cascade).
            if (!orderReceiptRepository.findByEventId(eventId).isEmpty()) {
                throw new BusinessRuleViolationException("Can't delete event with purchase history");
            }
            eventRepository.delete(eventId);
            log.info("Event {} deleted by user {}", eventId, userId);
        } finally {
            eventRepository.unlock(eventId);
        }
    }

    /**
     * Changes an event's status to a target state — non-cancel transitions only
     * (SCHEDULED or ON_SALE). Cancellation with the refund pipeline goes through
     * {@link #cancelEventAndRefund}. Holds the per-event write lock.
     *
     * @param token        the caller's token
     * @param eventId      the event whose status to change
     * @param targetStatus the target status (SCHEDULED or ON_SALE)
     * @throws InvalidTokenException          if the token is invalid
     * @throws UnauthorizedActionException    if the caller lacks {@code CONFIGURE_VENUE}
     * @throws InvalidStateTransitionException if {@code targetStatus} is not a manually settable state
     */
    public void changeEventStatus(String token, int eventId, EventStatus targetStatus) {
        int userId = validateTokenAndGetUserId(token);
        eventRepository.lockForUpdate(eventId);
        try {
            Event event = eventRepository.findById(eventId);
            User user = userRepository.getUserById(userId);
            user.requirePermissionInCompany(event.getCompanyId(), Permission.CONFIGURE_VENUE);
            switch (targetStatus) {
                case SCHEDULED -> event.transitionToScheduled();
                case ON_SALE -> event.transitionToOnSale();
                default -> throw new InvalidStateTransitionException("Cannot manually set status to " + targetStatus);
            }
            eventRepository.save(event);
            log.info("Event {} status changed to {} by user {}", eventId, targetStatus, userId);
        } finally {
            eventRepository.unlock(eventId);
        }
    }

    /**
     * UC-19 — soft-cancels an event and runs the UC-4 refund pipeline: for each
     * receipt with lines for this event it charges the gateway refund first,
     * validates the result, then marks the receipt refunded; afterwards it
     * refunds/voids the event's tickets, transitions the event to CANCELED, and
     * notifies ticket holders. The soft cancel keeps the event for
     * historical/reporting purposes. Idempotent on an already-CANCELED event.
     *
     * <p>Intentionally <em>not</em> {@code @Transactional}: it calls the external
     * payment gateway mid-flow, so its transaction boundary is restructured
     * separately (externals outside the tx) in V3-TX-02. Holds the per-event write
     * lock.
     *
     * @param token   the caller's token
     * @param eventId the event to cancel and refund
     * @throws InvalidTokenException       if the token is invalid
     * @throws UnauthorizedActionException if the caller lacks {@code CONFIGURE_VENUE}
     * @throws RuntimeException            if the event is missing or a refund fails
     */
    public void cancelEventAndRefund(String token, int eventId) {
        int ownerId = validateTokenAndGetUserId(token);
        // We lock the event for update to prevent concurrent modifications during the
        // cancellation and refund process. This ensures that we have a consistent view
        // of the event's state
        eventRepository.lockForUpdate(eventId);

        try {
            Event event = eventRepository.findById(eventId); // throws if not found, which is what we want here.
            User user = userRepository.getUserById(ownerId);
            user.requirePermissionInCompany(event.getCompanyId(), Permission.CONFIGURE_VENUE);

            if (event.getStatus() == EventStatus.CANCELED) {
                return;
            }

            List<OrderReceipt> orderReceipts = orderReceiptRepository.findByEventId(eventId);
            // For each receipt, we calculate the total refund amount for the lines related
            // to the canceled event, call the payment gateway to process the refund,
            // validate the refund result, and then update our domain state accordingly
            // (marking the receipt as refunded and updating ticket statuses). We also
            // handle various edge cases and potential errors along the way to ensure a
            // robust refund process.
            for (OrderReceipt receipt : orderReceipts) {
                double totalRefundForReceipt = receipt.getReceiptLines().stream()
                        .filter(line -> line.getEventId() == eventId)
                        .mapToDouble(ReceiptLine::getPriceAtReservation)
                        .sum();

                if (totalRefundForReceipt <= 0) {
                    continue;
                }
                // Extract the payment transaction ID from the receipt's transaction records.
                // This is necessary to tell the payment gateway which transaction we want to
                // refund. If we can't find a valid payment transaction ID, we throw an
                // exception and skip this receipt since we don't want to risk refunding without
                // a valid reference to the original charge.
                int paymentTransactionId = receipt.getPaymentTransactionId()
                        .orElseThrow(() -> new RefundFailedException(
                                receipt.getId(),
                                "receipt does not contain a payment transaction"));
                // Call the payment gateway to process the refund. This is a critical step that
                // must succeed before we make any changes to our domain state (e.g. marking
                // tickets as refunded or canceling the event) since we want to avoid
                // inconsistencies where we think we've refunded a customer but the payment
                // gateway disagrees.
                RefundResultDTO refundResult = paymentGateway.refund(
                        paymentTransactionId,
                        totalRefundForReceipt);
                // Validate the refund result before proceeding. If the refund failed for some
                // reason, we want to throw an exception and avoid making any changes to our
                // domain state (e.g. marking tickets as refunded or canceling the event) since
                // that could lead to inconsistencies.
                validateRefundResult(receipt.getId(), totalRefundForReceipt, refundResult);

                // If we reach this point, the refund was successful and we can update our
                // domain state accordingly.
                receipt.markRefunded(TransactionRecord.refund(
                        refundResult.refundTransactionId(),
                        paymentGateway.getId(),
                        refundResult.totalRefunded(),
                        receipt.getPaymentCurrency(),
                        refundResult.refundedAt()));

                orderReceiptRepository.save(receipt);
            }
            // After processing refunds for all receipts, we need to update the status of
            // all tickets for the event. For simplicity, we assume that if a ticket was
            // PAID or ISSUED, it should be marked as REFUNDED; otherwise, it can be marked
            // as VOIDED. In a real system, we might want to handle this more robustly (e.g.
            // consider different ticket statuses, handle partial refunds, etc.).
            List<Ticket> tickets = ticketRepository.findByEventId(eventId);
            // Note: we update ticket statuses after processing refunds to avoid
            // complications where we mark tickets as refunded but then encounter an error
            // during the refund process. By only updating ticket statuses after we've
            // successfully processed refunds, we can ensure that our domain state remains
            // consistent with the actual refund status of each order receipt.
            for (Ticket ticket : tickets) {
                if (ticket.getStatus() == TicketStatus.PAID || ticket.getStatus() == TicketStatus.ISSUED) {
                    ticket.markRefunded();
                } else {
                    ticket.markVoided();
                }
                ticketRepository.save(ticket);
            }
            // Finally, we mark the event as canceled. This is a soft cancel that allows us
            // to keep the event in our system for historical and reporting purposes while
            // preventing any future purchases or interactions with it. The event's status
            // will be used in various parts of the system (e.g. purchase flow, event
            // listings) to enforce the fact that it's canceled and should not be available
            // for purchase.
            event.transitionToCanceled("Event canceled by owner");
            eventRepository.save(event);

            // Notify all ticket holders
            notifyTicketHoldersOfCancellation(eventId, event.getName(), orderReceipts);

            log.info("Event {} canceled and refund flow completed", eventId);

        } catch (EventNotFoundException e) { // TODO: check if these Catches are good
            log.warn("Event {} not found for cancellation", eventId);
            throw new RuntimeException("Event not found");
        } catch (RefundFailedException e) {
            log.error("Refund failed during cancellation of event {}: {}", eventId, e.getMessage());
            throw new RuntimeException("Refund failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during cancellation of event {}: {}", eventId, e.getMessage());
            throw new RuntimeException("Error during cancellation: " + e.getMessage());

        } finally {
            eventRepository.unlock(eventId);
        }
    }

    /**
     * Notifies each distinct member ticket holder that an event was cancelled.
     * Failures per recipient are logged and skipped so one bad notification never
     * aborts the rest.
     *
     * @param eventId   the cancelled event's id
     * @param eventName the cancelled event's name
     * @param receipts  the receipts whose holders to notify
     */
    private void notifyTicketHoldersOfCancellation(int eventId, String eventName, List<OrderReceipt> receipts) {
        // Collect unique user IDs to avoid duplicate notifications per receipt
        java.util.Set<Integer> memberUserIds = receipts.stream()
                .filter(OrderReceipt::isMemberReceipt)
                .map(OrderReceipt::getHolderUserId)
                .filter(id -> id != null && id > 0)
                .collect(java.util.stream.Collectors.toSet());

        for (Integer userId : memberUserIds) {
            try {
                notificationService.notifyEventCancelled(userId, eventId, eventName);
            } catch (Exception e) {
                log.warn("Failed to send cancellation notification to userId={} for eventId={}", userId, eventId, e);
            }
        }
    }

    /**
     * Validates a refund result from the payment gateway before any domain state
     * is changed, centralizing the refund-validation logic for
     * {@link #cancelEventAndRefund}.
     *
     * @param receiptId            the receipt being refunded (for error context)
     * @param expectedRefundAmount the amount that should have been refunded
     * @param refundResult         the gateway's refund result
     * @throws RefundFailedException if the result is null, missing its transaction
     *                              id/timestamp, or the amount doesn't match
     */
    private void validateRefundResult(int receiptId, double expectedRefundAmount, RefundResultDTO refundResult) {
        if (refundResult == null) {
            throw new RefundFailedException(receiptId, "payment gateway returned null refund result");
        }

        if (refundResult.refundTransactionId() == null || refundResult.refundTransactionId().isBlank()) {
            throw new RefundFailedException(receiptId, "refund transaction id is missing");
        }

        if (Math.abs(refundResult.totalRefunded() - expectedRefundAmount) > 0.0001) {
            throw new RefundFailedException(receiptId, "refund amount mismatch");
        }

        if (refundResult.refundedAt() == null) {
            throw new RefundFailedException(receiptId, "refund timestamp is missing");
        }
    }

    /**
     * UC-21 — sets/replaces an event's purchase policy. The new event-specific
     * policy (built from the DTO) is ANDed with the company's policy (or
     * {@link NoPurchasePolicy} if the company has none) and stored on the event.
     * Owner-only. Holds the per-event write lock to avoid clobbering concurrent
     * edits.
     *
     * @param token  the caller's token
     * @param config the event/company ids and the new purchase-policy DTO
     * @throws RuntimeException            if the token is invalid, the company/event is missing,
     *                                    or the event does not belong to the company
     * @throws IllegalArgumentException    if the config is null or the policy DTO is malformed
     * @throws UnauthorizedActionException if the caller is not an owner
     */
    @Transactional
    public void setEventPolicies(String token, EventPolicyConfigDTO config) {
        if (!sessionManager.validateToken(token)) {
            throw new RuntimeException("Invalid token");
        }

        if (config == null) {
            throw new IllegalArgumentException("Event policy config cannot be null");
        }

        eventRepository.lockForUpdate(config.eventId());

        try {
            int userId = sessionManager.extractUserId(token);

            ProductionCompany company = companyRepository.getCompanyById(config.companyId());
            if (company == null) {
                throw new RuntimeException("Company not found");
            }

            User user = userRepository.getUserById(userId);
            if (user == null) {
                throw new RuntimeException("User not found");
            }
            user.requirePermissionInCompany(config.companyId(), Permission.EDIT_POLICIES);

            Event event = eventRepository.findById(config.eventId());
            if (event == null) {
                throw new RuntimeException("Event not found");
            }

            if (event.getCompanyId() != config.companyId()) {
                throw new RuntimeException("Event does not belong to this company");
            }

            PurchasePolicy companyPurchasePolicy = company.getPurchasePolicy();
            if (companyPurchasePolicy == null) {
                companyPurchasePolicy = new NoPurchasePolicy();
            }

            PurchasePolicy eventSpecificPurchasePolicy = buildPurchasePolicyFromDTO(config.purchasePolicy());

            PurchasePolicy inheritedAndExtendedPurchasePolicy = new AndPurchasePolicy(
                    companyPurchasePolicy,
                    eventSpecificPurchasePolicy);

            event.setPurchasePolicy(inheritedAndExtendedPurchasePolicy);

            eventRepository.save(event);

            log.info("Purchase policy for event {} was updated by user {}", event.getId(), userId);
        } finally {
            eventRepository.unlock(config.eventId());
        }
    }

    /**
     * Recursively builds a {@link PurchasePolicy} tree from its DTO. AGE /
     * MIN_TICKETS / MAX_TICKETS are leaves; AND/OR fold their children; NONE (or a
     * null DTO) yields a {@link NoPurchasePolicy}.
     *
     * @param dto the policy DTO (may be null)
     * @return the constructed policy tree
     * @throws IllegalArgumentException if the type is missing/unknown or a required
     *                                  field is absent
     */
    private PurchasePolicy buildPurchasePolicyFromDTO(PurchasePolicyDTO dto) {
        if (dto == null) {
            return new NoPurchasePolicy();
        }

        if (dto.type() == null || dto.type().isBlank()) {
            throw new IllegalArgumentException("Purchase policy type is required");
        }

        String type = dto.type().trim().toUpperCase();

        switch (type) {
            case "AGE":
                if (dto.minimumAge() == null) {
                    throw new IllegalArgumentException("minimumAge is required for AGE policy");
                }
                return new AgePurchasePolicy(dto.minimumAge());

            case "MIN_TICKETS":
                if (dto.minimumTickets() == null) {
                    throw new IllegalArgumentException("minimumTickets is required for MIN_TICKETS policy");
                }
                return new MinTicketsPurchasePolicy(dto.minimumTickets());

            case "MAX_TICKETS":
                if (dto.maximumTickets() == null) {
                    throw new IllegalArgumentException("maximumTickets is required for MAX_TICKETS policy");
                }
                return new MaxTicketsPurchasePolicy(dto.maximumTickets());

            case "AND":
                validateCompositeChildren(dto, "AND");
                return buildAndPolicy(dto.children());

            case "OR":
                validateCompositeChildren(dto, "OR");
                return buildOrPolicy(dto.children());

            case "NONE":
                return new NoPurchasePolicy();

            default:
                throw new IllegalArgumentException("Unknown purchase policy type: " + dto.type());
        }
    }

    /**
     * @param dto  the composite policy DTO
     * @param type the composite type name (for the error message)
     * @throws IllegalArgumentException if the composite has fewer than two children
     */
    private void validateCompositeChildren(PurchasePolicyDTO dto, String type) {
        if (dto.children() == null || dto.children().size() < 2) {
            throw new IllegalArgumentException(type + " policy must contain at least two children");
        }
    }

    /**
     * Folds the children into a left-nested {@link AndPurchasePolicy}.
     *
     * @param children the child policy DTOs (at least two)
     * @return the combined AND policy
     */
    private PurchasePolicy buildAndPolicy(List<PurchasePolicyDTO> children) {
        PurchasePolicy result = buildPurchasePolicyFromDTO(children.get(0));

        for (int i = 1; i < children.size(); i++) {
            result = new AndPurchasePolicy(
                    result,
                    buildPurchasePolicyFromDTO(children.get(i)));
        }

        return result;
    }

    /**
     * Folds the children into a left-nested {@link OrPurchasePolicy}.
     *
     * @param children the child policy DTOs (at least two)
     * @return the combined OR policy
     */
    private PurchasePolicy buildOrPolicy(List<PurchasePolicyDTO> children) {
        PurchasePolicy result = buildPurchasePolicyFromDTO(children.get(0));

        for (int i = 1; i < children.size(); i++) {
            result = new OrPurchasePolicy(
                    result,
                    buildPurchasePolicyFromDTO(children.get(i)));
        }

        return result;
    }

    /**
     * @param token the token to validate
     * @return the authenticated user's id
     * @throws InvalidTokenException if the token is invalid
     */
    private int validateTokenAndGetUserId(String token) {
        if (!sessionManager.validateToken(token)) {
            log.warn("Invalid token provided");
            throw new InvalidTokenException();
        }
        return sessionManager.extractUserId(token);
    }

    /**
     * UC-21 — returns an event's <em>event-specific</em> purchase policy as a DTO.
     * Since the stored policy is the company policy ANDed with the event policy,
     * the right branch (the event-specific part) is unwrapped before mapping.
     * Owner-only.
     *
     * @param token     the caller's token
     * @param companyId the company the event must belong to
     * @param eventId   the event whose policy to read
     * @return the event-specific purchase policy as a DTO
     * @throws RuntimeException            if the token is invalid, the company/event is missing,
     *                                    or the event does not belong to the company
     * @throws UnauthorizedActionException if the caller is not an owner
     */
    @Transactional(readOnly = true)
    public PurchasePolicyDTO getEventPurchasePolicy(String token, int companyId, int eventId) {
        if (!sessionManager.validateToken(token))
            throw new RuntimeException("Invalid token");
        int userId = sessionManager.extractUserId(token);
        ProductionCompany company = companyRepository.getCompanyById(companyId);
        if (company == null)
            throw new RuntimeException("Company not found");
        User user = userRepository.getUserById(userId);
        if (user == null)
            throw new RuntimeException("User not found");
        user.requirePermissionInCompany(companyId, Permission.EDIT_POLICIES);
        Event event = eventRepository.findById(eventId);
        if (event == null)
            throw new RuntimeException("Event not found");
        if (event.getCompanyId() != companyId)
            throw new RuntimeException("Event does not belong to this company");
        PurchasePolicy stored = event.getPurchasePolicy();
        if (stored instanceof AndPurchasePolicy a) {
            return policyToDTO(a.getRightPolicy());
        }
        return policyToDTO(stored);
    }

    /**
     * Recursively maps a {@link PurchasePolicy} tree to its DTO representation
     * (the inverse of {@link #buildPurchasePolicyFromDTO}).
     *
     * @param policy the policy tree (a null or {@link NoPurchasePolicy} maps to "NONE")
     * @return the policy as a DTO tree
     */
    private PurchasePolicyDTO policyToDTO(PurchasePolicy policy) {
        if (policy == null || policy instanceof NoPurchasePolicy)
            return new PurchasePolicyDTO("NONE", null, null, null, null);
        if (policy instanceof AgePurchasePolicy a)
            return new PurchasePolicyDTO("AGE", a.getMinimumAge(), null, null, null);
        if (policy instanceof MinTicketsPurchasePolicy m)
            return new PurchasePolicyDTO("MIN_TICKETS", null, m.getMinimumTickets(), null, null);
        if (policy instanceof MaxTicketsPurchasePolicy m)
            return new PurchasePolicyDTO("MAX_TICKETS", null, null, m.getMaximumTickets(), null);
        if (policy instanceof AndPurchasePolicy a)
            return new PurchasePolicyDTO("AND", null, null, null,
                    List.of(policyToDTO(a.getLeftPolicy()), policyToDTO(a.getRightPolicy())));
        if (policy instanceof OrPurchasePolicy o)
            return new PurchasePolicyDTO("OR", null, null, null,
                    List.of(policyToDTO(o.getLeftPolicy()), policyToDTO(o.getRightPolicy())));
        return new PurchasePolicyDTO("NONE", null, null, null, null);
    }
}
