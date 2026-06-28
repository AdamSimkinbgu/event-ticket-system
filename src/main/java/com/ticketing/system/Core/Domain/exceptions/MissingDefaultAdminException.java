package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown if {@code SystemAdminService} cannot create the default admin during
 * startup. I.1.4 — UC-1. Almost always indicates an environment/configuration
 * problem (missing seed credentials, DB unavailable for the bootstrap write).
 */
public class MissingDefaultAdminException extends DomainException {

    /**
     * @param reason why the default admin could not be established
     */
    public MissingDefaultAdminException(String reason) {
        super("Could not establish default System Admin: " + reason);
    }
}
