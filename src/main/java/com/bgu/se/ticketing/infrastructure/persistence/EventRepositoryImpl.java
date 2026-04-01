package com.bgu.se.ticketing.infrastructure.persistence;

import com.bgu.se.ticketing.domain.models.Event;
import com.bgu.se.ticketing.domain.repositories.IEventRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Infrastructure implementation of {@link IEventRepository} backed by Spring Data JPA.
 */
@Component
public class EventRepositoryImpl implements IEventRepository {

    private final SpringEventRepository springRepo;

    public EventRepositoryImpl(SpringEventRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public Event save(Event event) {
        EventJpaEntity entity = toEntity(event);
        EventJpaEntity saved = springRepo.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Event> findById(String id) {
        return springRepo.findById(id).map(this::toDomain);
    }

    @Override
    public List<Event> findAll() {
        return springRepo.findAll().stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Event> findByOrganizerId(String organizerId) {
        return springRepo.findByOrganizerId(organizerId).stream()
                .map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public void deleteById(String id) {
        springRepo.deleteById(id);
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private EventJpaEntity toEntity(Event event) {
        return new EventJpaEntity(event.getId(), event.getName(), event.getDescription(),
                event.getLocation(), event.getEventDateTime(), event.getTotalCapacity(),
                event.getAvailableTickets(), event.getTicketPrice(), event.getOrganizerId());
    }

    private Event toDomain(EventJpaEntity entity) {
        Event event = new Event(entity.getId(), entity.getName(), entity.getDescription(),
                entity.getLocation(), entity.getEventDateTime(), entity.getTotalCapacity(),
                entity.getTicketPrice(), entity.getOrganizerId());
        // Synchronize available tickets from the persisted state
        int alreadyReserved = entity.getTotalCapacity() - entity.getAvailableTickets();
        if (alreadyReserved > 0) {
            event.reserveTickets(alreadyReserved);
        }
        return event;
    }
}
