package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when a reservation requests more tickets than the zone has available.
 * UC-9, II.2.5 (selection mechanisms).
 */
public class InsufficientInventoryException extends DomainException {

    /**
     * @param zoneId    the zone with insufficient inventory
     * @param requested the number of tickets requested
     * @param available the number of tickets actually available
     */
    public InsufficientInventoryException(String zoneId, int requested, int available) {
        super("Zone " + zoneId + ": requested " + requested + " tickets, only " + available + " available");
    }

    /**
     * @param message custom detail message
     */
    public InsufficientInventoryException(String message) {
        super(message);
    }
}
