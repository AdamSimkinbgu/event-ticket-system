package com.bgu.se.ticketing.infrastructure;

import com.bgu.se.ticketing.domain.repositories.IEventRepository;
import com.bgu.se.ticketing.domain.services.TicketReservationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for domain service beans.
 *
 * <p>Domain services are plain Java objects with no Spring annotations – they are
 * wired here so that the domain layer stays framework-agnostic.
 */
@Configuration
public class DomainServiceConfig {

    @Bean
    public TicketReservationService ticketReservationService(IEventRepository eventRepository) {
        return new TicketReservationService(eventRepository);
    }
}
