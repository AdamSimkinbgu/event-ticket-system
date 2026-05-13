package com.ticketing.system.Infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.ticketing.system.Core.Application.dto.CatalogSearchFiltersDTO;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.eventCategory;

public class MemoryEventRepository implements IEventRepository {

    private final ConcurrentHashMap<Integer, Event> events = new ConcurrentHashMap<>();

    @Override
    public Event findById(int eventId) {
        return events.get(eventId);
    }

    @Override
    public boolean save(Event event) {
        events.put(event.getId(), event);
        return true;
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









    private boolean matchesSearch(Event event, CatalogSearchFiltersDTO filters) {
        // eventName — case-insensitive substring match on event name.
        if (filters.eventName() != null &&
                event.getName().toLowerCase().contains(filters.eventName().toLowerCase())) {
            return true;
        }

        // artistName — at least one artist must match (case-insensitive substring).
        if (filters.artistName() != null) {
            List<String> artists = event.getArtistsNames();
            if (artists != null && artists.stream().anyMatch(
                    a -> a.toLowerCase().contains(filters.artistName().toLowerCase()))) {
                return true;
            }
        }

        // category — exact enum match by name (case-insensitive).
        if (filters.category() != null) {
            try {
                eventCategory filterCategory = eventCategory.valueOf(filters.category().toUpperCase());
                if (event.getCategory() == filterCategory) {
                    return true;
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
            if (nameMatch || artistMatch) {
                return true;
            }
        }

        // fromDate / toDate — at least one ShowDate must fall within the range.
        if (filters.fromDate() != null || filters.toDate() != null) {
            boolean hasMatchingDate = event.getShowDates() != null &&
                    event.getShowDates().stream().anyMatch(sd -> {
                        LocalDate date = sd.getStartTime().toLocalDate();
                        if (filters.fromDate() != null && date.isBefore(filters.fromDate())) return false;
                        if (filters.toDate() != null && date.isAfter(filters.toDate())) return false;
                        return true;
                    });
            if (hasMatchingDate)
                return true;
        }
        
        // minPrice / maxPrice — at least one zone must be priced within the range.
        if (filters.minPrice() != null || filters.maxPrice() != null) {
            if (event.getVenueMap() == null || event.getVenueMap().getInventoryZones() == null) return false;
            boolean hasMatchingZone = event.getVenueMap().getInventoryZones().stream().anyMatch(z -> {
                double price = z.getprice();
                if (filters.minPrice() != null && price < filters.minPrice()) return false;
                if (filters.maxPrice() != null && price > filters.maxPrice()) return false;
                return true;
            });
            if (hasMatchingZone)
                return true;
        }

        //TODO: location, minRating, maxRating — not modelled on Event; not filtered here.   <<============================

        return false; // if any filter was set but didn't match, exclude the event.
    }
    
}
