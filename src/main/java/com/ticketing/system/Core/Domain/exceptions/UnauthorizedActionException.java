package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when an actor attempts an action they're not permitted to perform.
 * Per lecture 2: authorization is a domain concern, not just an app-service
 * concern. Used by ProductionCompany (UC-19/21/22/23/24/25), MemberAccountService
 * (UC-16), SystemAdminService (UC-31/32).
 */
public class UnauthorizedActionException extends DomainException {

    /**
     * @param action the action that was not permitted
     */
    public UnauthorizedActionException(String action) {
        super("Not authorized to perform action: " + action);
    }

    /**
     * @param action  the action that was not permitted
     * @param actorId the actor who attempted it
     */
    public UnauthorizedActionException(String action, Object actorId) {
        super("Actor " + actorId + " not authorized to perform action: " + action);
    }
}
