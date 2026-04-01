package com.bgu.se.ticketing.infrastructure.persistence;

import com.bgu.se.ticketing.domain.models.Ticket;
import com.bgu.se.ticketing.domain.models.TicketStatus;
import com.bgu.se.ticketing.domain.repositories.ITicketRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Infrastructure implementation of {@link ITicketRepository} backed by Spring Data JPA.
 */
@Component
public class TicketRepositoryImpl implements ITicketRepository {

    private final SpringTicketRepository springRepo;

    public TicketRepositoryImpl(SpringTicketRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public Ticket save(Ticket ticket) {
        TicketJpaEntity entity = toEntity(ticket, null);
        TicketJpaEntity saved = springRepo.save(entity);
        return toDomain(saved);
    }

    public Ticket saveWithOrder(Ticket ticket, String orderId) {
        TicketJpaEntity entity = toEntity(ticket, orderId);
        TicketJpaEntity saved = springRepo.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Ticket> findById(String id) {
        return springRepo.findById(id).map(this::toDomain);
    }

    @Override
    public List<Ticket> findByEventId(String eventId) {
        return springRepo.findByEventId(eventId).stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Ticket> findByOwnerId(String ownerId) {
        return springRepo.findByOwnerId(ownerId).stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Ticket> findByEventIdAndStatus(String eventId, TicketStatus status) {
        return springRepo.findByEventIdAndStatus(eventId, status).stream()
                .map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public void deleteById(String id) {
        springRepo.deleteById(id);
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private TicketJpaEntity toEntity(Ticket ticket, String orderId) {
        return new TicketJpaEntity(ticket.getId(), ticket.getEventId(),
                ticket.getOwnerId(), ticket.getPrice(), ticket.getStatus(), orderId);
    }

    private Ticket toDomain(TicketJpaEntity entity) {
        return new Ticket(entity.getId(), entity.getEventId(),
                entity.getOwnerId(), entity.getPrice(), entity.getStatus());
    }
}
