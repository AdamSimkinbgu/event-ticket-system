package com.bgu.se.ticketing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Event Management and Ticketing Platform.
 *
 * <p>The application follows a strict Domain-Driven Design (DDD) architecture
 * with the following layers:
 * <ul>
 *   <li><b>Domain</b>  – aggregates, entities, value objects, domain services, repository interfaces</li>
 *   <li><b>Application</b> – use-case services, DTOs</li>
 *   <li><b>Infrastructure</b> – JPA persistence, security, external gateways</li>
 *   <li><b>API</b> – REST controllers</li>
 * </ul>
 */
@SpringBootApplication
public class TicketingApplication {

    private static final Logger log = LoggerFactory.getLogger(TicketingApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(TicketingApplication.class, args);
        log.info("Event Ticketing Platform started successfully");
    }
}
