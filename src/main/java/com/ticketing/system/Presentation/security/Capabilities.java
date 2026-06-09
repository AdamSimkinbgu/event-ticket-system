package com.ticketing.system.Presentation.security;

import com.ticketing.system.Presentation.session.MockCompanies;
import com.ticketing.system.Presentation.session.MockPermissions;
import com.ticketing.system.Presentation.session.MockSession;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Single-source-of-truth resolver for the current session's
 * {@link Capability} set.
 *
 * <p>Callers use {@link #has(Capability)} from anywhere — drawer
 * builders, view bodies, button visibility checks. The resolution
 * itself reads the existing session-scoped state ({@link MockAuth},
 * {@link MockCompanies}, {@link MockSession},
 * {@link MockPermissions}) so this layer doesn't introduce new data;
 * it only synthesises a derived view of it.
 *
 * <p>When V2-AUTH-02 / V2-OWNER-WIRE-01 replace the mocks with real
 * services, this class is the one place the swap needs to land —
 * every view continues to call {@link #has(Capability)}.
 */
public final class Capabilities {

    private Capabilities() { }

    /** Capabilities a logged-out visitor automatically holds. */
    private static final Set<Capability> GUEST_BASE = unmodifiable(EnumSet.of(
        Capability.BROWSE_CATALOG,
        Capability.VIEW_EVENT_DETAILS,
        Capability.VIEW_SEAT_MAP,
        Capability.USE_CART
    ));

    /** Capabilities every signed-in member holds, on top of {@link #GUEST_BASE}. */
    private static final Set<Capability> MEMBER_BASE = unmodifiable(EnumSet.of(
        Capability.CHECKOUT,
        Capability.MEMBER_ACCOUNT,
        Capability.SUBMIT_COMPLAINT,
        Capability.REGISTER_COMPANY,
        Capability.MANAGE_INVITATIONS
    ));

    /** What founders + co-owners hold for their current company. */
    private static final Set<Capability> OWNER_BUNDLE = unmodifiable(EnumSet.of(
        // grantable
        Capability.VIEW_COMPANY_EVENTS,
        Capability.EDIT_COMPANY_EVENTS,
        Capability.VIEW_COMPANY_SALES,
        Capability.EDIT_PURCHASE_POLICIES,
        Capability.RESPOND_INQUIRIES,
        Capability.MANAGE_VENUE_MAPS,
        // owner-only
        Capability.APPOINT_MANAGER,
        Capability.APPOINT_CO_OWNER,
        Capability.EDIT_MANAGER_PERMISSIONS,
        Capability.REVOKE_MANAGER,
        Capability.CANCEL_EVENT
    ));

    /** Extra capabilities the founder holds on top of {@link #OWNER_BUNDLE}. */
    private static final Set<Capability> FOUNDER_EXTRA = unmodifiable(EnumSet.of(
        Capability.DISSOLVE_COMPANY,
        Capability.TRANSFER_FOUNDERSHIP
    ));

    /** Platform-admin capability bundle. */
    private static final Set<Capability> ADMIN_BUNDLE = unmodifiable(EnumSet.of(
        Capability.ADMIN_WORKSPACE,
        Capability.VIEW_GLOBAL_HISTORY,
        Capability.BROADCAST_ANNOUNCEMENT,
        Capability.MANAGE_COMPLAINTS,
        Capability.VIEW_ORG_TREES,
        Capability.ADMIN_SETTINGS
    ));

    /** Lookup helper — the common case. */
    public static boolean has(Capability c) {
        return forCurrentUser().contains(c);
    }

    /** True when none of the supplied capabilities are missing. */
    public static boolean hasAll(Capability... cs) {
        Set<Capability> mine = forCurrentUser();
        for (Capability c : cs) if (!mine.contains(c)) return false;
        return true;
    }

    /** True when at least one of the supplied capabilities is held. */
    public static boolean hasAny(Capability... cs) {
        Set<Capability> mine = forCurrentUser();
        for (Capability c : cs) if (mine.contains(c)) return true;
        return false;
    }

    /**
     * Compute the full capability set for the current session. Admin
     * caps stack with member caps — a user who is both an admin and a
     * member-with-Founder-role gets every capability from both bundles.
     * The unified sign-in form still routes by username, but the
     * runtime model now allows the dev panel (and any future "elevated
     * user" scenario) to mix the two.
     */
    public static Set<Capability> forCurrentUser() {
        Set<Capability> caps = EnumSet.copyOf(GUEST_BASE);

        // Admin caps stack independently — an admin can also be a member.
        if (MockAuth.isAdmin()) {
            caps.addAll(ADMIN_BUNDLE);
        }

        // Guest stops here (and so does an admin-without-signed-in edge case).
        if (!MockAuth.isSignedIn()) return caps;

        // Signed-in member baseline.
        caps.addAll(MEMBER_BASE);

        // Company role — what role does the current user hold in
        // the company currently in focus? May be null if they own none.
        MockCompanies.Company current = MockSession.currentCompany();
        if (current == null) return caps;

        caps.add(Capability.OWNER_WORKSPACE);

        String role = current.role();
        if (role == null) return caps;
        switch (role) {
            case "Founder" -> {
                caps.addAll(OWNER_BUNDLE);
                caps.addAll(FOUNDER_EXTRA);
            }
            case "Co-owner" -> caps.addAll(OWNER_BUNDLE);
            case "Manager" -> caps.addAll(MockPermissions.forCompany(current.id()));
            default -> { /* unknown role — workspace only */ }
        }
        return caps;
    }

    private static Set<Capability> unmodifiable(Set<Capability> src) {
        return Collections.unmodifiableSet(src);
    }
}
