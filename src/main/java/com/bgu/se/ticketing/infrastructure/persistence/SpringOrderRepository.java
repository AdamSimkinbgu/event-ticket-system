package com.bgu.se.ticketing.infrastructure.persistence;

import com.bgu.se.ticketing.domain.models.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link OrderJpaEntity}.
 */
@Repository
public interface SpringOrderRepository extends JpaRepository<OrderJpaEntity, String> {
    List<OrderJpaEntity> findByBuyerId(String buyerId);
    List<OrderJpaEntity> findByEventId(String eventId);
    List<OrderJpaEntity> findByStatus(OrderStatus status);
}
