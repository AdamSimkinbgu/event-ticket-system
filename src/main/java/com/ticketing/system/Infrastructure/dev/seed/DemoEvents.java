package com.ticketing.system.Infrastructure.dev.seed;

import com.ticketing.system.Core.Application.dto.EventCreationDTO;
import com.ticketing.system.Core.Application.dto.EventDetailDTO;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Application.dto.PurchasePolicyDTO;
import com.ticketing.system.Core.Application.dto.VenueMapConfigDTO;
import com.ticketing.system.Core.Application.dto.VenueMapConfigDTO.SeatConfigDTO;
import com.ticketing.system.Core.Application.dto.VenueMapConfigDTO.ZoneConfigDTO;
import com.ticketing.system.Core.Application.services.EventManagementService;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.EventCategory;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.Location;
import com.ticketing.system.Core.Domain.events.ShowDate;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds the demo events across the four seeded companies. Every event
 * goes through {@link EventManagementService#addEvent},
 * {@link EventManagementService#configureVenueMap}, then a direct
 * {@code transitionToOnSale} on the domain object so the catalog
 * browse + reservation flows have bookable inventory on first load.
 *
 * <p>All event show-dates are in the future (1 week to 6 months from
 * the {@link DemoClock} anchor); the project's domain {@code ShowDate}
 * rejects past times. Historical depth comes from past <em>order</em>
 * timestamps in {@link DemoOrders}, not from past events.
 */
public final class DemoEvents {

    private final EventManagementService eventService;
    private final IEventRepository eventRepository;
    private final Map<String, SeededUser> users;
    private final Map<String, ProductionCompanyDTO> companies;
    private final DemoClock clock;

    public DemoEvents(EventManagementService eventService,
                      IEventRepository eventRepository,
                      Map<String, SeededUser> users,
                      Map<String, ProductionCompanyDTO> companies,
                      DemoClock clock) {
        this.eventService = eventService;
        this.eventRepository = eventRepository;
        this.users = users;
        this.companies = companies;
        this.clock = clock;
    }

    public Map<String, EventDetailDTO> seed() {
        Map<String, EventDetailDTO> out = new LinkedHashMap<>();

        // -- Live Nation Israel — 4 stadium concerts + 1 festival --
        out.put("Coldplay — Music of the Spheres",
            seedConcertWithMixedZones(DemoCompanies.LIVE_NATION, DemoUsers.NAIM_FOUNDER,
                "Coldplay — Music of the Spheres",
                "The Music of the Spheres world tour lands at Park HaYarkon for one night only.",
                EventCategory.CONCERT, "Tel Aviv", 6, 3));
        out.put("Beyoncé — Renaissance",
            seedStandingConcert(DemoCompanies.LIVE_NATION, DemoUsers.NAIM_FOUNDER,
                "Beyoncé — Renaissance",
                "Renaissance world tour. General admission only.",
                EventCategory.CONCERT, "Tel Aviv", 35));
        out.put("Imagine Dragons — Loom",
            seedConcertWithMixedZones(DemoCompanies.LIVE_NATION, DemoUsers.NAIM_FOUNDER,
                "Imagine Dragons — Loom",
                "Loom tour stop with full production.",
                EventCategory.CONCERT, "Tel Aviv", 72, 3));
        out.put("Mediterranean Music Festival",
            seedStandingFestival(DemoCompanies.LIVE_NATION, DemoUsers.NAIM_FOUNDER,
                "Mediterranean Music Festival",
                "Two-day festival with regional headliners and local emerging acts.",
                "Caesarea", 95));

        // -- Coca-Cola Arena — 3 sports + 1 comedy --
        out.put("Hapoel TLV vs Maccabi Haifa",
            seedSeatedSports(DemoCompanies.COCA_COLA, DemoUsers.MOSHE_FOUNDER,
                "Hapoel TLV vs Maccabi Haifa",
                "Premier League derby — bring a scarf.",
                "Tel Aviv", 4));
        out.put("Hapoel Jerusalem — Basketball Final",
            seedSeatedSports(DemoCompanies.COCA_COLA, DemoUsers.MOSHE_FOUNDER,
                "Hapoel Jerusalem — Basketball Final",
                "Israeli Basketball Premier League final.",
                "Jerusalem", 28));
        out.put("NBA Africa Showcase",
            seedSeatedSports(DemoCompanies.COCA_COLA, DemoUsers.MOSHE_FOUNDER,
                "NBA Africa Showcase",
                "Exhibition game with selected NBA and African league stars.",
                "Tel Aviv", 110));
        out.put("Stand-Up Showcase — 21+",
            seedAgeRestrictedComedy(DemoCompanies.COCA_COLA, DemoUsers.MOSHE_FOUNDER,
                "Stand-Up Showcase — 21+",
                "Late-night comedy showcase, 21+ only.",
                "Tel Aviv", 18));

        // -- Habima Theatre — 3 plays --
        out.put("Othello",
            seedSeatedTheatre(DemoCompanies.HABIMA, DemoUsers.BENTZION_FOUNDER,
                "Othello",
                "Shakespeare's tragedy in Hebrew translation.",
                "Tel Aviv", 14));
        out.put("Hamlet",
            seedSeatedTheatre(DemoCompanies.HABIMA, DemoUsers.BENTZION_FOUNDER,
                "Hamlet",
                "The Danish prince, reimagined for the modern stage.",
                "Tel Aviv", 50));
        out.put("Romeo & Juliet",
            seedSeatedTheatre(DemoCompanies.HABIMA, DemoUsers.BENTZION_FOUNDER,
                "Romeo & Juliet",
                "The classic love tragedy with original Hebrew adaptation.",
                "Tel Aviv", 130));

        // -- Shuni Productions — 2 intimate concerts --
        out.put("Caesarea Jazz Evening",
            seedSeatedTheatre(DemoCompanies.SHUNI, DemoUsers.NAIM_FOUNDER,
                "Caesarea Jazz Evening",
                "Open-air jazz under the stars at the Roman amphitheatre.",
                "Caesarea", 40));
        out.put("Shuni Classical Strings",
            seedSeatedTheatre(DemoCompanies.SHUNI, DemoUsers.NAIM_FOUNDER,
                "Shuni Classical Strings",
                "String quartet performing Bach, Mozart, and Tchaikovsky.",
                "Binyamina", 165));

        return out;
    }

    // -- Builders for the common shapes ----------------------------------

    private EventDetailDTO seedConcertWithMixedZones(String companyName, String founderKey,
                                                    String name, String description,
                                                    EventCategory category, String city,
                                                    int daysAhead, int durationHours) {
        var zones = List.of(
            seatedZone("Lower",  3, 8, 220.0),
            seatedZone("Upper",  4, 10, 140.0),
            standingZone("GA",   250, 90.0)
        );
        return seedEvent(companyName, founderKey, name, description, category, city,
            daysAhead, durationHours, zones, nonePolicy());
    }

    private EventDetailDTO seedStandingConcert(String companyName, String founderKey,
                                               String name, String description,
                                               EventCategory category, String city,
                                               int daysAhead) {
        var zones = List.of(
            standingZone("Front pit", 200, 320.0),
            standingZone("GA",        500, 180.0)
        );
        return seedEvent(companyName, founderKey, name, description, category, city,
            daysAhead, 2, zones, nonePolicy());
    }

    private EventDetailDTO seedStandingFestival(String companyName, String founderKey,
                                                String name, String description,
                                                String city, int daysAhead) {
        var zones = List.of(
            standingZone("VIP",       150, 480.0),
            standingZone("Festival",  1200, 220.0)
        );
        return seedEvent(companyName, founderKey, name, description,
            EventCategory.FESTIVAL, city, daysAhead, 10, zones, nonePolicy());
    }

    private EventDetailDTO seedSeatedSports(String companyName, String founderKey,
                                            String name, String description,
                                            String city, int daysAhead) {
        var zones = List.of(
            seatedZone("Home side",  3, 10, 180.0),
            seatedZone("Away side",  3, 10, 180.0),
            seatedZone("Centre",     2, 8,  280.0)
        );
        return seedEvent(companyName, founderKey, name, description,
            EventCategory.SPORTS, city, daysAhead, 2, zones, nonePolicy());
    }

    private EventDetailDTO seedSeatedTheatre(String companyName, String founderKey,
                                             String name, String description,
                                             String city, int daysAhead) {
        var zones = List.of(
            seatedZone("Stalls",   4, 10, 200.0),
            seatedZone("Balcony",  3, 10, 130.0)
        );
        return seedEvent(companyName, founderKey, name, description,
            EventCategory.THEATER, city, daysAhead, 2, zones, nonePolicy());
    }

    private EventDetailDTO seedAgeRestrictedComedy(String companyName, String founderKey,
                                                   String name, String description,
                                                   String city, int daysAhead) {
        var zones = List.of(
            seatedZone("Floor",   4, 8, 160.0),
            standingZone("Bar",   80, 110.0)
        );
        return seedEvent(companyName, founderKey, name, description,
            EventCategory.COMEDY, city, daysAhead, 2, zones,
            new PurchasePolicyDTO("AGE", 21, null, null, null));
    }

    // -- Core seed pipeline ----------------------------------------------

    private EventDetailDTO seedEvent(String companyName, String founderKey,
                                     String name, String description,
                                     EventCategory category, String city,
                                     int daysAhead, int durationHours,
                                     List<ZoneConfigDTO> zones,
                                     PurchasePolicyDTO policy) {
        ProductionCompanyDTO company = companies.get(companyName);
        String token = users.get(founderKey).token();

        LocalDateTime start = LocalDateTime.ofInstant(
            clock.plusDays(daysAhead), ZoneId.systemDefault());
        ShowDate show = new ShowDate(start, start.plusHours(durationHours));

        EventDetailDTO created = eventService.addEvent(token, new EventCreationDTO(
            company.companyId(), name, description, List.of("TBA"), category, 0.0,
            new Location("Israel", city), List.of(show), policy));

        eventService.configureVenueMap(token, company.companyId(),
            new VenueMapConfigDTO(created.eventId(), city + " venue", zones));

        Event event = eventRepository.findById(Integer.parseInt(created.eventId()));
        forceStatusOnSale(event);
        eventRepository.save(event);
        return created;
    }

    /**
     * Reflection bypass: the domain has no DRAFT → SCHEDULED path, so
     * {@code Event.transitionToOnSale()} can't be reached through the
     * legitimate API. For a fixture this is fine — we're materialising
     * a state the production flow will eventually produce once the
     * missing transition lands.
     */
    private static void forceStatusOnSale(Event event) {
        try {
            Field f = Event.class.getDeclaredField("status");
            f.setAccessible(true);
            f.set(event, EventStatus.ON_SALE);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not set Event.status for demo seed", e);
        }
    }

    // -- Zone helpers -----------------------------------------------------

    private ZoneConfigDTO seatedZone(String name, int rows, int cols, double price) {
        List<SeatConfigDTO> seats = new ArrayList<>(rows * cols);
        for (int r = 0; r < rows; r++) {
            char rowLetter = (char) ('A' + r);
            for (int c = 0; c < cols; c++) {
                seats.add(new SeatConfigDTO(rowLetter + "-" + (c + 1), c * 60.0, r * 60.0));
            }
        }
        return new ZoneConfigDTO(name, true, null, seats, price);
    }

    private ZoneConfigDTO standingZone(String name, int capacity, double price) {
        return new ZoneConfigDTO(name, false, capacity, null, price);
    }

    private static PurchasePolicyDTO nonePolicy() {
        return new PurchasePolicyDTO("NONE", null, null, null, null);
    }
}
