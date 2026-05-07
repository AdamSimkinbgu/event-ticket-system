package com.ticketing.system.Core.Domain.events;

public interface EventRepository {
    Event findById(String eventId);

    boolean save(Event event);
}