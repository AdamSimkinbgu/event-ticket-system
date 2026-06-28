package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when an operation references an appointment that doesn't exist.
 * UC-23 (responding to a non-existent invitation), UC-24 (editing/revoking a
 * non-existent appointment).
 */
public class AppointmentNotFoundException extends DomainException {

    /**
     * @param companyId the company the appointment was expected at
     * @param userId    the user the appointment was expected for
     */
    public AppointmentNotFoundException(Object companyId, Object userId) {
        super("No appointment found for user " + userId + " at company " + companyId);
    }

    /**
     * @param message custom detail message
     */
    public AppointmentNotFoundException(String message) {
        super(message);
    }
}
