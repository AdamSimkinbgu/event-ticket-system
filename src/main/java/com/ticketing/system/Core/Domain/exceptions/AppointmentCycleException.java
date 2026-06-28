package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when an appointment would create a cycle in the company's appointment
 * tree. II.4.8.3 — UC-23 (Appoint Co-Owner). Enforced by
 * {@code ProductionCompany.canAppoint(...)}.
 */
public class AppointmentCycleException extends DomainException {

    /**
     * @param appointerId the user making the appointment
     * @param appointeeId the user being appointed
     */
    public AppointmentCycleException(Object appointerId, Object appointeeId) {
        super("Appointment from " + appointerId + " to " + appointeeId
              + " would create a cycle in the appointment tree");
    }
}
