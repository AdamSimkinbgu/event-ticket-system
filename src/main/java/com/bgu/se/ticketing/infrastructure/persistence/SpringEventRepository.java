package com.bgu.se.ticketing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link EventJpaEntity}.
 */
@Repository
public interface SpringEventRepository extends JpaRepository<EventJpaEntity, String> {
    List<EventJpaEntity> findByOrganizerId(String organizerId);
}
