package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when a permission-check fails on a CompanyAppointment. Differs from
 * {@link UnauthorizedActionException}: this is specifically about a Manager not
 * having the right Permission value granted to them. UC-19/21/24.
 */
public class InvalidPermissionException extends DomainException {

    /**
     * @param requiredPermission the permission that was missing
     */
    public InvalidPermissionException(String requiredPermission) {
        super("Missing required permission: " + requiredPermission);
    }

    /**
     * @param requiredPermission the permission that was missing
     * @param userId             the user who lacked the permission
     */
    public InvalidPermissionException(String requiredPermission, Object userId) {
        super("User " + userId + " lacks required permission: " + requiredPermission);
    }
}
