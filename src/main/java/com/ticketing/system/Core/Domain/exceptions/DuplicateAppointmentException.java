package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when attempting to appoint someone who is already a Manager / Owner /
 * pending. UC-24 — and the "one appointer per user" invariant from II.4.8.3.
 */
public class DuplicateAppointmentException extends DomainException {

    /**
     * @param userId    the user who already has an appointment
     * @param companyId the company at which the appointment already exists
     */
    public DuplicateAppointmentException(Object userId, Object companyId) {
        super("User " + userId + " already has an appointment at company " + companyId);
    }
}
