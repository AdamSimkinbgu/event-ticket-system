package com.ticketing.system.Presentation.security;

/**
 * Marker interface for views that require a signed-in platform admin.
 *
 * <p>Picked up by {@link AuthBootstrap}: views implementing this marker
 * forward signed-out users + non-admin users to the dedicated
 * {@code AdminLoginView}, keeping the system-admin workspace
 * unreachable from a regular member's perspective.
 */
public interface RequiresAdminRole { }
