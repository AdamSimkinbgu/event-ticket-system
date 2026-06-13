package com.ticketing.system.Infrastructure.persistence;

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
import com.ticketing.system.Core.Domain.exceptions.UserNotFoundException;

import org.springframework.stereotype.Repository;

@Repository
public class MemoryEventRepository implements IEventRepository {

    private final ConcurrentHashMap<Integer, Event> events = new ConcurrentHashMap<>();
    private final AtomicInteger idSequence = new AtomicInteger(1);
    private final AtomicInteger venueMapIdSequence = new AtomicInteger(1);
    private final RepositoryLocks<Integer> locks = new RepositoryLocks<>();    // Key is eventId for event-level locks.

    @Override
    public void lockForUpdate(Integer id) {
        locks.lock(id);
    }

    @Override
    public void unlock(Integer id) {
        locks.unlock(id);
    }

    @Override
    public int nextId() {
        return idSequence.getAndIncrement();
    }

    @Override
    public int nextVenueMapId() {
        return venueMapIdSequence.getAndIncrement();
    }

    @Override
    public Event findById(int eventId) {
        if (!events.containsKey(eventId)) {
            throw new EventNotFoundException("Event with ID " + eventId + " not found");
        }
        return events.get(eventId);
    }

    @Override
    public boolean save(Event event) {
        events.put(event.getId(), event);
        return true;
        // In a real implementation, we might return false if the save failed for some
        // reason (e.g. DB error);
        // here we'll just assume it always works.
    }

    @Override
    public List<Event> findByCompanyId(int companyId) {
        return events.values().stream()
                .filter(e -> e.getCompanyId() == companyId)
                .collect(Collectors.toList());
    }

    @Override
    public List<Integer> findIdsByCompany(int companyId) {
        return events.values().stream()
                .filter(e -> e.getCompanyId() == companyId)
                .map(Event::getId)
                .collect(Collectors.toList());
    }

    @Override
    public List<Event> findActiveByCompany(int companyId) {
        return events.values().stream()
                .filter(e -> e.getCompanyId() == companyId && e.getStatus() == EventStatus.ON_SALE)
                .collect(Collectors.toList());
    }

    @Override
    public List<Event> findByStatus(EventStatus status) {
        return events.values().stream()
                .filter(e -> e.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public List<Event> search(CatalogSearchFiltersDTO filters) {
        return events.values().stream()
                .filter(e -> matchesSearch(e, filters))
                .collect(Collectors.toList());
    }

    /*
     * A helper method to apply the various search filters to an event; used in the
     * search() implementation above.
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

        return true; // if the event passed into all the filters, it got to here and we'll return
                     // true
    }

}
