package com.ticketing.system.Core.Application.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import com.ticketing.system.Core.Domain.exceptions.*;

import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Domain.events.ShowDate;
import com.ticketing.system.Core.Application.dto.CatalogSearchFiltersDTO;
import com.ticketing.system.Core.Application.dto.CompanySummaryDTO;
import com.ticketing.system.Core.Application.dto.EventDetailDTO;
import com.ticketing.system.Core.Application.dto.EventSummaryDTO;
import com.ticketing.system.Core.Application.dto.SearchResultDTO;
import com.ticketing.system.Core.Application.dto.VenueMapDTO;
import com.ticketing.system.Core.Application.dtoMappers.VenueMapMapper;
import com.ticketing.system.Core.Application.dtoMappers.EventMapper;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.company.CompanyStatus;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.events.IEventRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side service for guest- and visitor-facing catalog queries. Owns UC-3
 * (Browse Events as Guest), UC-7 (Browse + Search Catalogs), UC-8 (View Venue
 * Map + Inventory).
 *
 * <p>Separated from {@code EventManagementService} (which is owner-side /
 * write-heavy) so the two audiences don't share an API surface — see
 * {@code design_walkthrough_summary.md} §6. All public reads accept either a JWT
 * (Member) or a raw sessionId (Guest): the catalog is open to anyone with an
 * active session, and only ON_SALE events of ACTIVE companies are exposed.
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class CatalogService {

    private final ISessionManager sessionManager;
    private final IEventRepository eventRepository;
    private final IProductionCompanyRepository productionCompanyRepository;
    private final ITicketRepository ticketRepository;

    public CatalogService(
            ISessionManager sessionManager,
            IEventRepository eventRepository,
            IProductionCompanyRepository productionCompanyRepository,
            ITicketRepository ticketRepository
    ) {
        this.sessionManager = sessionManager;
        this.eventRepository = eventRepository;
        this.productionCompanyRepository = productionCompanyRepository;
        this.ticketRepository = ticketRepository;
    }



    /**
     * UC-3 — browses the catalog, returning every ON_SALE event of an ACTIVE
     * company. Open to anyone with an active session (auth rework #181 /
     * Phase 4.3).
     *
     * @return the public catalog as event summaries
     */
    public List<EventSummaryDTO> browseEventCatalog() {
        EventMapper mapper = new EventMapper();
        // Return all events that are ON_SALE and associated with an ACTIVE company.
        return productionCompanyRepository.findActive().stream()
                .flatMap(company -> eventRepository.findActiveByCompany(company.getCompanyId()).stream())
                .filter(event -> event.getStatus() == EventStatus.ON_SALE)
                .map(event -> mapper.convertEventToEventSummaryDTO(event, productionCompanyRepository))
                .toList();
    }




    /**
     * V2-LANDING-01 — featured events for the public landing page: the ON_SALE
     * events from active companies, ordered best-rated first (events without a
     * rating sort last), capped at {@code limit}. No credential required: the
     * landing page is anonymous. There is no curation flag on Event, so
     * "featured" is a rating-based heuristic for now.
     *
     * @param limit the maximum number of events to return (negative is treated as 0)
     * @return the top-rated events, as summaries
     */
    public List<EventSummaryDTO> featured(int limit) {
        EventMapper mapper = new EventMapper();
        return productionCompanyRepository.findActive().stream()
                .flatMap(company -> eventRepository.findActiveByCompany(company.getCompanyId()).stream())
                .sorted(Comparator.comparing(Event::getRating, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(0, limit))
                .map(event -> mapper.convertEventToEventSummaryDTO(event, productionCompanyRepository))
                .toList();
    }




    /**
     * V2-LANDING-01 — "on sale soon" teaser for the landing page: SCHEDULED
     * events (a venue map is bound but sales haven't opened) from active
     * companies, soonest show date first, capped at {@code limit}. Sourced from
     * {@code findByStatus(SCHEDULED)} (since {@code findActiveByCompany} returns
     * ON_SALE only), dropping any whose company isn't active.
     *
     * @param limit the maximum number of events to return (negative is treated as 0)
     * @return the soonest upcoming events, as summaries
     */
    public List<EventSummaryDTO> upcomingOnSale(int limit) {
        EventMapper mapper = new EventMapper();
        return eventRepository.findByStatus(EventStatus.SCHEDULED).stream()
                .filter(event -> activeCompanyOrNull(event.getCompanyId()) != null)
                .sorted(Comparator.comparing(this::earliestShowStart, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(Math.max(0, limit))
                .map(event -> mapper.convertEventToEventSummaryDTO(event, productionCompanyRepository))
                .toList();
    }

    /**
     * @param event the event
     * @return the earliest show start time, or {@code null} if it somehow has no
     *         show dates (an Event invariant requires &gt;=1, so the null branch
     *         only guards against future changes)
     */
    private LocalDateTime earliestShowStart(Event event) {
        return event.getShowDates().stream()
                .map(ShowDate::getStartTime)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }




    /**
     * UC-7 — searches the catalog with filters, across publicly-visible ON_SALE
     * events of active companies. Open to anyone with an active session (auth
     * rework #181 / Phase 4.3).
     *
     * @param credential a JWT (Member) or raw sessionId (Guest)
     * @param filters    the optional search filters (price/rating/date/etc.)
     * @return matching events as summaries
     * @throws InvalidTokenException     if the credential is invalid
     * @throws IllegalArgumentException  if a numeric range or the date range is invalid
     */
    public List<EventSummaryDTO> searchGlobal(String credential, CatalogSearchFiltersDTO filters) {
        log.info("Starting global search with filters: {}", filters);
        // Validate the credential (JWT or sessionId) before proceeding with the search.
        if (!this.sessionManager.validateCredential(credential)) {
            log.warn("Invalid credential provided while performing global search with filters: {}", filters);
            throw new InvalidTokenException("Invalid credential provided while performing global search with filters: " + filters);
        }
        // Normalize and validate the filters, THROWING ---> IllegalArgumentException for invalid ranges or dates.
        CatalogSearchFiltersDTO effectiveFilters = normalizeAndValidateCatalogFilters(filters);
        // Create an EventMapper to convert Event entities to EventSummaryDTOs.
        EventMapper mapper = new EventMapper();

        List<EventSummaryDTO> results = new ArrayList<>();

        // Iterate over all events returned by the eventRepository.searchONSALE() method, applying the effective filters.
        for (Event event : eventRepository.searchONSALE(effectiveFilters)) {
            // Check if the event is associated with an active company and is publicly visible (ON_SALE).
            
            ProductionCompany company = activeCompanyOrNull(event.getCompanyId());

            // If the event is not publicly visible or the company does not match the company rating filters, skip to the next event.
            if (!isCompanyPubliclyVisible(company)) {
                continue;
            }
            if (!matchesCompanyRating(company, effectiveFilters)) {
                continue;
            }

            results.add(mapper.convertEventToEventSummaryDTO(event, productionCompanyRepository));
        }

        log.info("Global search with filters: {} returning {} results", effectiveFilters, results.size());
        return results;
    }





    /**
     * Like {@link #searchGlobal} but scoped to a specific company and without the
     * company-rating filters.
     *
     * @param credential a JWT (Member) or raw sessionId (Guest)
     * @param companyId  the company to scope the search to
     * @param filters    the optional search filters (company-rating filters are dropped)
     * @return matching events of that company as summaries
     * @throws InvalidTokenException      if the credential is invalid
     * @throws CompanyNotFoundException   if the company does not exist
     * @throws CompanyClosedException     if the company is not active
     * @throws IllegalArgumentException   if a numeric range or the date range is invalid
     */
    public List<EventSummaryDTO> searchByCompany(String credential, int companyId, CatalogSearchFiltersDTO filters) {
        log.info("Starting company-scoped search for companyId: {} with filters: {}", companyId, filters);
        // Validate the credential (JWT or sessionId) before proceeding with the search.
        if (!this.sessionManager.validateCredential(credential)) {
            log.warn("Invalid credential provided while performing company-scoped search for companyId: {} with filters: {}",
                    companyId, filters);
            throw new InvalidTokenException("Invalid credential provided while performing company-scoped search for companyId: " + companyId);
        }

        // Validate that the specified company exists and is active, throwing CompanyNotFoundException or CompanyClosedException if not.
        requireActiveCompany(companyId);

        // Normalize and validate the filters, THROWING ---> IllegalArgumentException for invalid ranges or dates. 
        // Without company-rating filters since this is a company-scoped search.
        CatalogSearchFiltersDTO effectiveFilters = normalizeAndValidateCatalogFilters(filters).withoutCompanyRating();
        EventMapper mapper = new EventMapper();

        List<EventSummaryDTO> results = new ArrayList<>();

        for (Event event : eventRepository.searchONSALE(effectiveFilters)) {

            if (event.getCompanyId() != companyId) {
                continue;
            }
            // Check if the event is associated with an active company and is publicly visible (ON_SALE). this check is 
            ProductionCompany company = activeCompanyOrNull(event.getCompanyId());
            if (!isCompanyPubliclyVisible(company)) {
                continue;
            }

            results.add(mapper.convertEventToEventSummaryDTO(event, productionCompanyRepository));
        }

        log.info("Company-scoped search for companyId: {} with filters: {} returning {} results",
                companyId, effectiveFilters, results.size());

        return results;
    }




    /**
     * V2-SEARCH-01 (#281) — top-bar search across events, artists and venues.
     * Matches the query (case-insensitive substring) over publicly-visible
     * ON_SALE events from ACTIVE companies and emits typed rows; artists/venues
     * are de-duplicated. There are no dedicated artist/venue pages, so every row
     * points at a representative event. Results are round-robined across the
     * three types so one kind can't crowd out the others, then capped to
     * {@code limit}.
     *
     * @param credential a JWT (Member) or raw sessionId (Guest)
     * @param query      the search text; a blank query yields no results
     * @param limit      the per-type and overall result cap; {@code <= 0} yields no results
     * @return up to {@code limit} typed search rows
     * @throws InvalidTokenException if the credential is invalid
     */
    public List<SearchResultDTO> search(String credential, String query, int limit) {
        if (!this.sessionManager.validateCredential(credential)) {
            log.warn("Invalid credential provided while performing top-bar search");
            throw new InvalidTokenException();
        }
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty() || limit <= 0) {
            return List.of();
        }

        List<SearchResultDTO> events = new ArrayList<>();
        List<SearchResultDTO> artists = new ArrayList<>();
        List<SearchResultDTO> venues = new ArrayList<>();
        Set<String> seenArtists = new LinkedHashSet<>();
        Set<String> seenVenues = new LinkedHashSet<>();

        for (Event event : eventRepository.findByStatus(EventStatus.ON_SALE)) {
            if (events.size() >= limit && artists.size() >= limit && venues.size() >= limit) {
                break; // every bucket is full — nothing more can surface
            }
            ProductionCompany company = activeCompanyOrNull(event.getCompanyId());
            if (company == null) {
                continue; // closed / unknown company — not publicly visible
            }

            String eventName = event.getName();
            if (events.size() < limit && eventName != null && eventName.toLowerCase().contains(q)) {
                events.add(new SearchResultDTO("EVENT", eventName, company.getName(), event.getId()));
            }

            if (artists.size() < limit && event.getArtistsNames() != null) {
                for (String artist : event.getArtistsNames()) {
                    if (artists.size() >= limit) {
                        break;
                    }
                    if (artist != null && artist.toLowerCase().contains(q)
                            && seenArtists.add(artist.toLowerCase())) {
                        artists.add(new SearchResultDTO("ARTIST", artist, "Artist · " + eventName, event.getId()));
                    }
                }
            }

            String venue = venueLabel(event);
            if (venues.size() < limit && venue != null && venue.toLowerCase().contains(q)
                    && seenVenues.add(venue.toLowerCase())) {
                venues.add(new SearchResultDTO("VENUE", venue, "Venue · " + eventName, event.getId()));
            }
        }

        List<SearchResultDTO> results = new ArrayList<>();
        boolean added = true;
        for (int i = 0; results.size() < limit && added; i++) {
            added = false;
            if (i < events.size())  { results.add(events.get(i));  added = true; if (results.size() == limit) break; }
            if (i < artists.size()) { results.add(artists.get(i)); added = true; if (results.size() == limit) break; }
            if (i < venues.size())  { results.add(venues.get(i));  added = true; if (results.size() == limit) break; }
        }
        log.info("Top-bar search for '{}' returning {} results", q, results.size());
        return results;
    }

    /**
     * Active production companies whose name contains the (case-insensitive) query
     * substring, sorted by name. Backs the member "New Inquiry" company picker
     * (II.3.10). A blank query returns all active companies so the picker can show
     * an initial list. No credential needed — company names are public.
     *
     * @param substring the name fragment to match; blank returns all active companies
     * @return matching active companies as summaries, sorted by name
     */
    public List<CompanySummaryDTO> searchCompaniesByName(String substring) {
        String needle = substring == null ? "" : substring.trim().toLowerCase();
        return productionCompanyRepository.findActive().stream()
                .filter(c -> needle.isEmpty()
                        || (c.getName() != null && c.getName().toLowerCase().contains(needle)))
                .sorted(Comparator.comparing(ProductionCompany::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(c -> new CompanySummaryDTO(c.getCompanyId(), c.getName(), c.getRating()))
                .toList();
    }

    /**
     * @param event the event
     * @return a "city, country" label for the event's venue, or {@code null} if it
     *         has no location
     */
    private String venueLabel(Event event) {
        if (event.getVenueMap() == null || event.getVenueMap().getLocation() == null) {
            return null;
        }
        return event.getVenueMap().getLocation().toString();
    }

    






    /**
     * Normalizes and validates the search filters.
     *
     * @param filters the raw filters (may be null)
     * @return the normalized filters
     * @throws IllegalArgumentException if a price/rating range is inverted or
     *                                  {@code fromDate} is after {@code toDate}
     */
    private CatalogSearchFiltersDTO normalizeAndValidateCatalogFilters(CatalogSearchFiltersDTO filters) {
        CatalogSearchFiltersDTO f = filters == null
                ? CatalogSearchFiltersDTO.empty()
                : filters.normalized();

        validateDoubleRange(f.minPrice(), f.maxPrice(), "price");
        validateDoubleRange(f.minEventRating(), f.maxEventRating(), "event rating");
        validateDoubleRange(f.minCompanyRating(), f.maxCompanyRating(), "company rating");

        // validate that fromDate is before or equal to toDate if both are provided.
        if (f.fromDate() != null && f.toDate() != null && f.fromDate().isAfter(f.toDate())) {
            throw new IllegalArgumentException("fromDate must be before or equal to toDate");
        }

        return f;
    }

    /**
     * Validates a min/max range. A null bound is unbounded and therefore valid.
     *
     * @param min  the lower bound, or {@code null}
     * @param max  the upper bound, or {@code null}
     * @param name the range name, used in the error message
     * @throws IllegalArgumentException if both bounds are present and {@code min > max}
     */
    private void validateDoubleRange(Double min, Double max, String name) {
        // if either min or max is null, we consider it unbounded and therefore valid, so only throw if both are non-null and min > max.
        if (min != null && max != null && min > max) {
            throw new IllegalArgumentException("min " + name + " must be <= max " + name);
        }
    }

    /**
     * Looks up a company, treating "not found", "not active", and any lookup
     * exception uniformly as "not publicly visible".
     *
     * @param companyId the company id
     * @return the company if it exists and is ACTIVE, otherwise {@code null}
     */
    private ProductionCompany activeCompanyOrNull(int companyId) {
        try {
            ProductionCompany company = productionCompanyRepository.getCompanyById(companyId);
            // will throw if company not found, and if it doesn't throw but the company is not active, we'll return null to indicate that the company is not publicly visible.
            if (company == null || company.getStatus() != CompanyStatus.ACTIVE) {
                return null;
            }
            return company;
        } catch (RuntimeException e) {
            // If the company is not found or any other exception occurs, return null to indicate that the company does not exist.
            return null;
        }
    }

    /**
     * Requires that a company exists and is active.
     *
     * @param companyId the company id
     * @return the active company
     * @throws CompanyNotFoundException if the company does not exist
     * @throws CompanyClosedException   if the company is not active
     */
    private ProductionCompany requireActiveCompany(int companyId) {
        ProductionCompany company;
        try {
            company = productionCompanyRepository.getCompanyById(companyId);
        } catch (RuntimeException e) {
            throw new CompanyNotFoundException(companyId);
        }

        if (company == null) {
            throw new CompanyNotFoundException(companyId);
        }

        if (company.getStatus() != CompanyStatus.ACTIVE) {
            throw new CompanyClosedException(companyId);
        }

        return company;
    }

    /**
     * @param company the company (may be null)
     * @return {@code true} if the company is non-null and ACTIVE
     */
    private boolean isCompanyPubliclyVisible(ProductionCompany company) {
        return company != null && company.getStatus() == CompanyStatus.ACTIVE;
    }

    /**
     * @param company the company to test
     * @param filters the filters carrying the optional company-rating bounds
     * @return {@code true} if the company's rating falls within the supplied
     *         min/max company-rating bounds (an unrated company fails a present bound)
     */
    private boolean matchesCompanyRating(ProductionCompany company, CatalogSearchFiltersDTO filters) {
        Double rating = company.getRating();

        if (filters.minCompanyRating() != null && (rating == null || rating < filters.minCompanyRating())) {
            return false;
        }

        if (filters.maxCompanyRating() != null && (rating == null || rating > filters.maxCompanyRating())) {
            return false;
        }

        return true;
    }




    






    /**
     * UC-8 — renders the venue map with per-seat / per-zone availability. Open to
     * anyone with an active session (auth rework #181 / Phase 4.3).
     *
     * @param credential a JWT (Member) or raw sessionId (Guest)
     * @param eventId    the event whose venue map to render
     * @return the venue map as a DTO
     * @throws InvalidTokenException   if the credential is invalid
     * @throws EventNotFoundException  if the event does not exist
     * @throws CompanyClosedException  if the owning company is not active
     * @throws NullVenueMapException   if the event has no venue map bound
     */
    public VenueMapDTO getEventVenueMap(String credential, int eventId) {
        log.info("Fetching venue map for eventId: {}", eventId);

            if (!this.sessionManager.validateCredential(credential)) {
                log.warn("Invalid credential provided while getting venue map for eventId: {}", eventId);
                throw new InvalidTokenException();
            }
            
            // see that event exists and has a venue map
            Event event = this.eventRepository.findById(eventId);
            if (event == null) {
                log.warn("Event not found while getting venue map: {}", eventId);
                throw new EventNotFoundException("Event with ID " + eventId + " not found while getting venue map");
            }

            // Additional check to enforce company status is ACTIVE.
            ProductionCompany company = productionCompanyRepository.getCompanyById(event.getCompanyId());
            if (company == null || company.getStatus() != CompanyStatus.ACTIVE) {
                log.warn("Attempt to access venue map for eventId: {} from inactive company", eventId);
                throw new CompanyClosedException("Event with ID " + eventId + " not found while getting venue map");
            }
            
            // see that event has a venue map
            if (event.getVenueMap() == null) {
                log.warn("Null venue map for event while getting venue map: {}", eventId);
                throw new NullVenueMapException(eventId);
            }

            log.info("Venue map found for eventId: {} while getting venue map and being returned", eventId);
            return new VenueMapMapper().venueMapToVenueMapDTO(event.getVenueMap());

    }


    /**
     * UC-8 — public single-event detail for the buyer event page (header,
     * description, schedule, lineup). Open to anyone with an active session.
     * DRAFT / SCHEDULED events are not yet public; ON_SALE / SOLD_OUT / CANCELED /
     * COMPLETED are viewable (the page renders a status badge and disables
     * purchasing for the non-purchasable ones).
     *
     * @param credential a JWT (Member) or raw sessionId (Guest)
     * @param eventId    the event whose detail to fetch
     * @return the event detail as a DTO
     * @throws InvalidTokenException   if the credential is invalid
     * @throws EventNotFoundException  if the event does not exist or is not yet public
     * @throws CompanyClosedException  if the owning company is not active
     */
    public EventDetailDTO getEventDetail(String credential, int eventId) {
        log.info("Fetching event detail for eventId: {}", eventId);

        if (!this.sessionManager.validateCredential(credential)) {
            log.warn("Invalid credential provided while getting event detail for eventId: {}", eventId);
            throw new InvalidTokenException();
        }

        Event event = this.eventRepository.findById(eventId);
        if (event == null) {
            log.warn("Event not found while getting event detail: {}", eventId);
            throw new EventNotFoundException("Event with ID " + eventId + " not found while getting event detail");
        }

        // Enforce company status is ACTIVE — closed companies' events are not publicly visible.
        ProductionCompany company = productionCompanyRepository.getCompanyById(event.getCompanyId());
        if (company == null || company.getStatus() != CompanyStatus.ACTIVE) {
            log.warn("Attempt to access event detail for eventId: {} from inactive company", eventId);
            throw new CompanyClosedException("Event with ID " + eventId + " not found while getting event detail");
        }

        // DRAFT / SCHEDULED events are not yet public; ON_SALE / SOLD_OUT / CANCELED / COMPLETED are viewable
        // (the buyer page renders a status badge and disables purchasing for the non-purchasable ones).
        if (event.getStatus() == EventStatus.DRAFT || event.getStatus() == EventStatus.SCHEDULED) {
            log.warn("Attempt to access non-public event detail (status {}) for eventId: {}", event.getStatus(), eventId);
            throw new EventNotFoundException("Event with ID " + eventId + " not found while getting event detail");
        }

        log.info("Event detail found for eventId: {} and being returned", eventId);
        return new EventMapper().toEventDetailDTO(event, company.getName());
    }

}
