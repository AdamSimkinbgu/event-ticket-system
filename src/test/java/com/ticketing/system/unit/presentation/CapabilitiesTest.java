package com.ticketing.system.unit.presentation;

import com.ticketing.system.Presentation.security.Capabilities;
import com.ticketing.system.Presentation.security.Capability;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sanity checks for the capability resolver. We can't easily fake a
 * VaadinSession inside a unit test, so the assertions here cover the
 * static structure (bundle sets, helper booleans) — the per-role bundle
 * compositions and the marker / annotation wiring are exercised by
 * VaadinSmokeTest's view-construction pass.
 */
class CapabilitiesTest {

    @Test
    void resolverIsCallableWithoutSession() {
        Set<Capability> caps = Capabilities.forCurrentUser();
        assertNotNull(caps, "Capabilities.forCurrentUser() must never return null");
    }

    @Test
    void guestBaseIncludesCatalogButNotCheckout() {
        // No VaadinSession in this test → resolver short-circuits to GUEST_BASE.
        Set<Capability> caps = Capabilities.forCurrentUser();
        assertTrue(caps.contains(Capability.BROWSE_CATALOG),
            "Guest must be able to browse the catalog");
        assertFalse(caps.contains(Capability.CHECKOUT),
            "Guest must NOT have CHECKOUT — that's a member capability");
        assertFalse(caps.contains(Capability.OWNER_WORKSPACE),
            "Guest must NOT have OWNER_WORKSPACE — that requires a company");
        assertFalse(caps.contains(Capability.ADMIN_WORKSPACE),
            "Guest must NOT have ADMIN_WORKSPACE — admins go through their own pool");
    }

    @Test
    void hasAndHasAllAndHasAnyAreConsistent() {
        // With only the guest baseline available, these helpers must agree.
        assertTrue(Capabilities.has(Capability.BROWSE_CATALOG));
        assertTrue(Capabilities.hasAll(Capability.BROWSE_CATALOG, Capability.VIEW_EVENT_DETAILS));
        assertTrue(Capabilities.hasAny(Capability.BROWSE_CATALOG, Capability.CHECKOUT));
        assertFalse(Capabilities.hasAll(Capability.BROWSE_CATALOG, Capability.CHECKOUT));
        assertFalse(Capabilities.hasAny(Capability.CHECKOUT, Capability.OWNER_WORKSPACE));
    }
}
