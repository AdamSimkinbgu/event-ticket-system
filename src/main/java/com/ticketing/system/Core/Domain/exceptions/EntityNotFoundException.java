package com.ticketing.system.Core.Domain.exceptions;

/**
 * Generic "looked up by ID, not found" — used by repositories and any service
 * doing a by-ID lookup.
 */
public class EntityNotFoundException extends DomainException {

    /**
     * @param entityType the kind of entity that was searched for (e.g. "Event")
     * @param id         the id that was looked up
     */
    public EntityNotFoundException(String entityType, Object id) {
        super(entityType + " not found: " + id);
    }

    /**
     * @param message custom detail message
     */
    public EntityNotFoundException(String message) {
        super(message);
    }
}
