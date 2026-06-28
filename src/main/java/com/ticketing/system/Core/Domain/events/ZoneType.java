package com.ticketing.system.Core.Domain.events;

/**
 * Whether a venue zone has assigned seats or is general-admission standing room.
 * Determines which inventory/selection mechanism applies (II.2.5).
 */
public enum ZoneType {
    /** General-admission standing room — inventory is a simple available count. */
    STANDING,
    /** Assigned seating — each seat is an individually reservable ticket. */
    SEATED
}
