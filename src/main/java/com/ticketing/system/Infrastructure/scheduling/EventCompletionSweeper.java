package com.ticketing.system.Infrastructure.scheduling;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.exceptions.EventNotFoundException;

/**
 * Lifecycle sweeper: scans live events (ON_SALE / SOLD_OUT) whose last show date has
 * passed and transitions them to {@link EventStatus#COMPLETED}.
 *
 * <p>Runs on a fixed delay (default 60s; override via {@code event-completion.fixed-delay-ms}).
 * Once an event is COMPLETED the existing gates take over automatically: the buyer detail
 * view disables purchasing ("Event Ended" / "PAST" badge), {@code CheckoutService} rejects
 * it, and the buyer catalog/search (ON_SALE only) drop it.
 *
 * <p>The sweep is exposed as a public method so tests can drive it directly with a
 * controllable {@code now} (no need to wait for the {@code @Scheduled} cadence).
 */
@Component
@Slf4j
public class EventCompletionSweeper {

    private final IEventRepository eventRepository;
    private final Clock clock;

    public EventCompletionSweeper(IEventRepository eventRepository, Clock clock) {
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${event-completion.fixed-delay-ms:60000}")
    public void sweep() {
        int completed = completeFinishedEvents(LocalDateTime.now(clock));
        if (completed > 0) {
            log.info("event-completion tick: {} event(s) marked COMPLETED", completed);
        }
    }

    /**
     * Completes every live event whose last show has ended as of {@code now}.
     * Returns the number of events transitioned. Public so unit tests can drive the
     * sweep independently of the {@code @Scheduled} cadence.
     *
     * <p>Each event is locked, re-read, re-validated, mutated and unlocked one at a time
     * — never holding more than one event lock — so there is no lock-ordering concern with
     * the multi-event {@code SessionAndOrderSweeper}.
     */
    public int completeFinishedEvents(LocalDateTime now) {
        // Unlocked scan: collect candidates, then re-validate each under its write lock below.
        List<Event> candidates = new ArrayList<>();
        candidates.addAll(eventRepository.findByStatus(EventStatus.ON_SALE));
        candidates.addAll(eventRepository.findByStatus(EventStatus.SOLD_OUT));

        int completed = 0;
        for (Event candidate : candidates) {
            if (!candidate.hasFinishedAsOf(now)) {
                continue;
            }
            int eventId = candidate.getId();
            eventRepository.lockForUpdate(eventId);
            try {
                Event fresh = eventRepository.findById(eventId);
                // Re-validate under the write lock: status/dates may have changed since the
                // unlocked scan (owner cancel, inventory release reverting SOLD_OUT, a prior
                // tick). transitionToCompleted is idempotent, but checking first avoids the
                // guard throwing on a now-stale candidate.
                if ((fresh.getStatus() == EventStatus.ON_SALE || fresh.getStatus() == EventStatus.SOLD_OUT)
                        && fresh.hasFinishedAsOf(now)) {
                    fresh.transitionToCompleted();
                    eventRepository.save(fresh);
                    completed++;
                }
            } catch (EventNotFoundException e) {
                // Event was deleted between the scan and acquiring the lock — nothing to do.
                log.warn("event-completion: event {} vanished before completion; skipping", eventId);
            } catch (RuntimeException e) {
                // One bad event must not abort the rest of the tick.
                log.warn("event-completion: failed to complete event {}; continuing", eventId, e);
            } finally {
                eventRepository.unlock(eventId);
            }
        }
        return completed;
    }
}
