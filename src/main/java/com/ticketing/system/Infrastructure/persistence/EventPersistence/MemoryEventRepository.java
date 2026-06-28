package com.ticketing.system.Infrastructure.persistence.EventPersistence;

import com.ticketing.system.Infrastructure.persistence.RepositoryReadWriteLocks;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.ticketing.system.Core.Application.dto.CatalogSearchFiltersDTO;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.EventCategory;
import com.ticketing.system.Core.Domain.exceptions.EventNotFoundException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * In-memory adapter for {@link IEventRepository}, active under every profile
 * except {@code jpa} (the JPA adapter takes over when {@code @Profile("jpa")} is
 * selected). Backed by a {@link ConcurrentHashMap} keyed by event id, with
 * {@link AtomicInteger} id sequences for events and venue maps.
 *
 * <p>Concurrency model: a {@link RepositoryReadWriteLocks} provides per-event
 * read/write locks. Lifecycle writes ({@link #save}, {@link #delete}) require the
 * calling thread to hold the event's <em>write</em> lock; buyer operations take a
 * <em>read</em> lock so reservations can run concurrently but never alongside a
 * lifecycle change. Search/finder methods are lock-free snapshots over the map's
 * values.
 */
@Repository
@Profile("!jpa")
public class MemoryEventRepository implements IEventRepository {

    private final ConcurrentHashMap<Integer, Event> events = new ConcurrentHashMap<>();
    private final AtomicInteger idSequence = new AtomicInteger(1);
    private final AtomicInteger venueMapIdSequence = new AtomicInteger(1);
    private final RepositoryReadWriteLocks<Integer> locks = new RepositoryReadWriteLocks<>();  // per-event locks for lifecycle synchronization

    /**
     * Acquires the write lock for an event before a lifecycle mutation.
     *
     * @param id the event id to lock
     */
    @Override
    public void lockForUpdate(Integer id) {
        locks.lockWrite(id);
    }

    /**
     * Releases the write lock acquired by {@link #lockForUpdate}.
     *
     * @param id the event id to unlock
     */
    @Override
    public void unlock(Integer id) {
        locks.unlockWrite(id);
    }

    /**
     * Acquires a read lock so a buyer operation (e.g. reservation) can run
     * without racing a lifecycle write.
     *
     * @param eventId the event id to read-lock
     */
    @Override
    public void lockForBuyerOperation(int eventId) {
        locks.lockRead(eventId);
    }

    /**
     * Releases the read lock acquired by {@link #lockForBuyerOperation}.
     *
     * @param eventId the event id to unlock
     */
    @Override
    public void unlockBuyerOperation(int eventId) {
        locks.unlockRead(eventId);
    }

    /**
     * @return the next available event id
     */
    @Override
    public int nextId() {
        return idSequence.getAndIncrement();
    }

    /**
     * @return the next available venue-map id
     */
    @Override
    public int nextVenueMapId() {
        return venueMapIdSequence.getAndIncrement();
    }

    /**
     * @param eventId the event id to look up
     * @return the event
     * @throws EventNotFoundException if no event with that id exists
     */
    @Override
    public Event findById(int eventId) {
        if (!events.containsKey(eventId)) {
            throw new EventNotFoundException("Event with ID " + eventId + " not found");
        }
        return events.get(eventId);
    }

    /**
     * Removes an event. A delete is a structural/lifecycle change, so the calling
     * thread must hold the event's write lock (mirroring {@link #save}).
     *
     * @param eventId the event id to delete
     * @throws IllegalStateException if the event exists but is not write-locked
     *                               by the current thread
     */
    @Override
    public void delete(int eventId) {
        // Delete is a structural/lifecycle change: an existing event must be write-locked
        // by the calling thread, mirroring save(), so it can't race with buyer read locks
        // (lockForBuyerOperation) or other lifecycle writes.
        if (events.containsKey(eventId)
                && !locks.isWriteHeldByCurrentThread(eventId)) {
            throw new IllegalStateException("Event " + eventId + " must be locked before deleting");
        }
        events.remove(eventId);
    }

    /**
     * Persists an event. A brand-new event may be saved without a lock (no other
     * thread can know its id yet); an existing event must be read- or write-locked
     * by the calling thread to prevent unguarded read-modify-write races.
     *
     * @param event the event to persist
     * @return always {@code true}
     * @throws IllegalStateException if the event already exists but is not locked
     *                               by the current thread
     */
    @Override
    public boolean save(Event event) {
        // New events (not yet in the map) may be saved without a lock — no other
        // thread can know the ID before the first save completes.
        // Existing events must be locked by the calling thread to prevent
        // unguarded read-modify-write races.
        if (events.containsKey(event.getId())
                && !locks.isWriteHeldByCurrentThread(event.getId())
                && !locks.isReadHeldByCurrentThread(event.getId())) {
            throw new IllegalStateException("Event " + event.getId() + " must be locked before saving");
        }
        events.put(event.getId(), event);
        return true;
    }

    /**
     * @param companyId the owning company's id
     * @return all events belonging to the company, regardless of status
     */
    @Override
    public List<Event> findByCompanyId(int companyId) {
        return events.values().stream()
                .filter(e -> e.getCompanyId() == companyId)
                .collect(Collectors.toList());
    }

    /**
     * @param companyId the owning company's id
     * @return the ids of all events belonging to the company
     */
    @Override
    public List<Integer> findIdsByCompany(int companyId) {
        return events.values().stream()
                .filter(e -> e.getCompanyId() == companyId)
                .map(Event::getId)
                .collect(Collectors.toList());
    }

    /**
     * @param companyId the owning company's id
     * @return the company's events that are currently ON_SALE
     */
    @Override
    public List<Event> findActiveByCompany(int companyId) {
        return events.values().stream()
                .filter(e -> e.getCompanyId() == companyId && e.getStatus() == EventStatus.ON_SALE)
                .collect(Collectors.toList());
    }

    /**
     * @param status the status to filter by
     * @return all events in the given status
     */
    @Override
    public List<Event> findByStatus(EventStatus status) {
        return events.values().stream()
                .filter(e -> e.getStatus() == status)
                .collect(Collectors.toList());
    }

    /**
     * Public-catalog search: like {@link #searchAll} but restricted to ON_SALE
     * events.
     *
     * @param filters the optional catalog filters
     * @return matching events that are ON_SALE
     */
    @Override
    public List<Event> searchONSALE(CatalogSearchFiltersDTO filters) {
        // we'll just call the full search and then filter ON_SALE in-memory since this is an in-memory repo;
        // a real DB implementation would push the ON_SALE filter down into the query for efficiency.
        return searchAll(filters).stream()
                .filter(e -> e.getStatus() == EventStatus.ON_SALE)
                .collect(Collectors.toList());
    }

    /**
     * UC-7 — global search with multiple optional filters (any combination of
     * name, artist, category, keywords, date range, price range, rating range and
     * location), across events of every status.
     *
     * @param filters the optional catalog filters; null fields are ignored
     * @return all events matching every supplied filter
     */
    @Override
    public List<Event> searchAll(CatalogSearchFiltersDTO filters) {
        return events.values().stream()
                .filter(e -> matchesSearch(e, filters))
                .collect(Collectors.toList());
    }

    /**
     * Tests one event against every supplied (non-null) filter, used by
     * {@link #searchAll}. Each filter is independent and ANDed together; a null
     * filter field is treated as "no constraint".
     *
     * @param event   the event to test
     * @param filters the filters to apply
     * @return {@code true} if the event satisfies every supplied filter
     */
    private boolean matchesSearch(Event event, CatalogSearchFiltersDTO filters) {
        // eventName — case-insensitive substring match on event name.
        if (filters.eventName() != null &&
                !event.getName().toLowerCase().contains(filters.eventName().toLowerCase())) {
            return false;
        }

        // artistName — at least one artist must match (case-insensitive substring).
        if (filters.artistName() != null) {
            if (event.getArtistsNames() == null ||
                    !event.getArtistsNames().stream().anyMatch(
                            a -> a.toLowerCase().contains(filters.artistName().toLowerCase()))) {
                return false;
            }
        }

        // category — exact enum match by name (case-insensitive).
        if (filters.category() != null) {
            try {
                EventCategory filterCategory = EventCategory.valueOf(filters.category().toUpperCase());
                if (event.getCategory() != filterCategory) {
                    return false;
                }
            } catch (IllegalArgumentException e) {
                return false; // unknown category value — no event can match.
            }
        }

        // keywords — case-insensitive substring match on event name or any artist name.
        if (filters.keywords() != null) {
            String kw = filters.keywords().toLowerCase();
            boolean nameMatch = event.getName() != null && event.getName().toLowerCase().contains(kw);
            boolean artistMatch = event.getArtistsNames() != null &&
                    event.getArtistsNames().stream().anyMatch(a -> a.toLowerCase().contains(kw));
            if (!nameMatch && !artistMatch) {
                return false;
            }
        }

        // fromDate / toDate — at least one ShowDate must fall within the range.
        if (filters.fromDate() != null || filters.toDate() != null) {
            boolean hasMatchingDate = event.getShowDates() != null && event.getShowDates().stream().anyMatch(sd -> {
                LocalDate date = sd.getStartTime().toLocalDate();
                if (filters.fromDate() != null && date.isBefore(filters.fromDate()))
                    return false;
                if (filters.toDate() != null && date.isAfter(filters.toDate()))
                    return false;
                return true;
            });
            if (!hasMatchingDate)
                return false;
        }

        // minPrice / maxPrice — at least one zone must be priced within the range.
        if (filters.minPrice() != null || filters.maxPrice() != null) {
            if (event.getVenueMap() == null || event.getVenueMap().getInventoryZones() == null)
                return false;
            boolean hasMatchingZone = event.getVenueMap().getInventoryZones().stream().anyMatch(z -> {
                double price = z.getprice();
                if (filters.minPrice() != null && price < filters.minPrice())
                    return false;
                if (filters.maxPrice() != null && price > filters.maxPrice())
                    return false;
                return true;
            });
            if (!hasMatchingZone)
                return false;
        }

        // minEventRating / maxEventRating — event rating must fall within the range.
        if (filters.minEventRating() != null
                && (event.getRating() == null || event.getRating() < filters.minEventRating())) {
            return false;
        }
        if (filters.maxEventRating() != null
                && (event.getRating() == null || event.getRating() > filters.maxEventRating())) {
            return false;
        }

        // location — event venue city or country must match (case-insensitive
        // substring).
        if (filters.location() != null) {
            if (event.getVenueMap() == null || event.getVenueMap().getLocation() == null)
                return false;
            String locFilter = filters.location().toLowerCase();
            boolean cityMatch = event.getVenueMap().getLocation().city().toLowerCase().contains(locFilter);
            boolean countryMatch = event.getVenueMap().getLocation().country().toLowerCase().contains(locFilter);
            if (!cityMatch && !countryMatch) {
                return false;
            }
        }

        return true; // if the event passed into all the filters, it got to here and we'll return true
    }


}
