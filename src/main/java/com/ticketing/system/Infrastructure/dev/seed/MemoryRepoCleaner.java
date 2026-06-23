package com.ticketing.system.Infrastructure.dev.seed;

import com.ticketing.system.Infrastructure.persistence.MemoryActiveOrderRepository;
import com.ticketing.system.Infrastructure.persistence.MemoryAdminRepository;
import com.ticketing.system.Infrastructure.persistence.MemoryConversationRepository;
import com.ticketing.system.Infrastructure.persistence.MemoryEventRepository;
import com.ticketing.system.Infrastructure.persistence.MemoryNotificationRepository;
import com.ticketing.system.Infrastructure.persistence.MemoryOrderReceiptRepository;
import com.ticketing.system.Infrastructure.persistence.MemoryProductionCompanyRepository;
import com.ticketing.system.Infrastructure.persistence.MemorySessionRepository;
import com.ticketing.system.Infrastructure.persistence.MemoryTicketRepository;
import com.ticketing.system.Infrastructure.persistence.MemoryUserRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Wipes every in-memory repository back to an empty state. Used by
 * a {@code seed.mode=wipe} run so a reset starts from a
 * deterministic clean slate without restarting the JVM.
 *
 * <p>Uses reflection rather than adding {@code clear()} methods to each
 * memory repo because (a) {@code clear()} would have no place on the
 * domain {@code IRepository} interface and (b) the same reflection
 * works the day a new memory repo is added — just add it to the
 * constructor list. Resets non-final instance Maps, Collections, and
 * AtomicInteger / AtomicLong counters; leaves locks and final
 * configuration alone.
 *
 * <p>{@code @Profile("dev")} only — never instantiated in production.
 */
@Component
@Profile("dev")
@Slf4j
public class MemoryRepoCleaner {

    private final List<Object> repositories;

    public MemoryRepoCleaner(
            MemoryUserRepository userRepo,
            MemorySessionRepository sessionRepo,
            MemoryActiveOrderRepository activeOrderRepo,
            MemoryEventRepository eventRepo,
            MemoryTicketRepository ticketRepo,
            MemoryOrderReceiptRepository orderReceiptRepo,
            MemoryNotificationRepository notificationRepo,
            MemoryConversationRepository conversationRepo,
            MemoryProductionCompanyRepository companyRepo,
            MemoryAdminRepository adminRepo) {
        this.repositories = List.of(
            userRepo, sessionRepo, activeOrderRepo, eventRepo, ticketRepo,
            orderReceiptRepo, notificationRepo, conversationRepo, companyRepo, adminRepo
        );
    }

    public void clearAll() {
        for (Object repo : repositories) {
            try {
                reset(repo);
            } catch (RuntimeException e) {
                log.warn("failed to reset {}: {}", repo.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    private static void reset(Object repo) {
        for (Field f : repo.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
            } catch (RuntimeException ignored) {
                continue;
            }
            Object value;
            try {
                value = f.get(repo);
            } catch (IllegalAccessException ignored) {
                continue;
            }
            if (value instanceof Map<?, ?> m) {
                m.clear();
            } else if (value instanceof Collection<?> c) {
                c.clear();
            } else if (value instanceof AtomicInteger a) {
                a.set(1);
            } else if (value instanceof AtomicLong a) {
                a.set(1L);
            }
        }
    }
}
