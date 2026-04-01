package com.bgu.se.ticketing.infrastructure.persistence;

import com.bgu.se.ticketing.domain.models.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link TicketJpaEntity}.
 */
@Repository
public interface SpringTicketRepository extends JpaRepository<TicketJpaEntity, String> {
    List<TicketJpaEntity> findByEventId(String eventId);
    List<TicketJpaEntity> findByOwnerId(String ownerId);
    List<TicketJpaEntity> findByEventIdAndStatus(String eventId, TicketStatus status);
}
