package com.ticketing.system.Core.Domain.events;

/**
 * High-level classification of an event, used for catalog browsing and filtering
 * (UC-3 / UC-7).
 */
public enum EventCategory {
    /** Live music that doesn't fit the narrower {@link #CONCERT} category. */
    MUSIC,
    /** Stage theater and drama. */
    THEATER,
    /** Sporting events. */
    SPORTS,
    /** Stand-up and comedy shows. */
    COMEDY,
    /** Multi-act or multi-day festivals. */
    FESTIVAL,
    /** A single-headliner concert. */
    CONCERT,
    /** Anything that doesn't fit the other categories. */
    OTHER
}
