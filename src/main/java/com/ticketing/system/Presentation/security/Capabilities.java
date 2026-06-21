package com.ticketing.system.Presentation.security;

import com.ticketing.system.Core.Application.dto.UserCompanyDTO;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Domain.users.Permission;
import com.ticketing.system.Presentation.session.AuthSession;
import com.ticketing.system.Presentation.session.CompanyManagementBridge;
import com.ticketing.system.Presentation.session.CurrentCompanies;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Single-source-of-truth resolver for the current session's
 * {@link Capability} set.
 *
 * <p>Callers use {@link #has(Capability)} from anywhere — drawer
 * builders, view bodies, button visibility checks. The resolution
 * itself reads the existing session-scoped state ({@link AuthSession},
 * {@link CurrentCompanies}) so this layer doesn't introduce new data;
 * it only synthesises a derived view of it.
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
        Capability.VIEW_COMPANY_EVENTS,
        Capability.EDIT_COMPANY_EVENTS,
        Capability.VIEW_COMPANY_SALES,
        Capability.EDIT_PURCHASE_POLICIES,
        Capability.RESPOND_INQUIRIES,
        Capability.MANAGE_VENUE_MAPS,
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

    public static boolean has(Capability c) {
        return forCurrentUser().contains(c);
    }

    public static boolean hasAll(Capability... cs) {
        Set<Capability> mine = forCurrentUser();
        for (Capability c : cs) {
            if (!mine.contains(c)) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasAny(Capability... cs) {
        Set<Capability> mine = forCurrentUser();
        for (Capability c : cs) {
            if (mine.contains(c)) {
                return true;
            }
        }
        return false;
    }

    public static Set<Capability> forCurrentUser() {
        Set<Capability> caps = EnumSet.copyOf(GUEST_BASE);

        if (AuthSession.isAdmin()) {
            caps.addAll(ADMIN_BUNDLE);
        }

        if (!AuthSession.isSignedIn()) {
            return caps;
        }

        caps.addAll(MEMBER_BASE);

        Integer userId = AuthSession.userId();
        CompanyManagementService service = CompanyManagementBridge.service();
        if (userId == null || service == null) {
            return caps;
        }

        List<UserCompanyDTO> memberships = service.listForUser(userId);
        if (memberships.isEmpty()) {
            return caps;
        }

        Integer currentId = CurrentCompanies.currentCompanyId();
        UserCompanyDTO current = null;
        if (currentId != null) {
            for (UserCompanyDTO m : memberships) {
                if (m.companyId() == currentId) {
                    current = m;
                    break;
                }
            }
        }
        if (current == null) {
            current = memberships.get(0);
        }

        caps.add(Capability.OWNER_WORKSPACE);

        String role = current.role();
        if (role == null) {
            return caps;
        }
        switch (role) {
            case "Founder" -> {
                caps.addAll(OWNER_BUNDLE);
                caps.addAll(FOUNDER_EXTRA);
            }
            case "Co-owner" -> caps.addAll(OWNER_BUNDLE);
            case "Manager" -> caps.addAll(capabilitiesFromPermissions(current.managerPermissions()));
            default -> { /* unknown role — workspace only */ }
        }
        return caps;
    }

    private static Set<Capability> capabilitiesFromPermissions(List<Permission> permissions) {
        Set<Capability> caps = EnumSet.noneOf(Capability.class);
        if (permissions == null) {
            return caps;
        }
        for (Permission permission : permissions) {
            Capability mapped = switch (permission) {
                case VIEW_SALES -> Capability.VIEW_COMPANY_SALES;
                case EDIT_POLICIES -> Capability.EDIT_PURCHASE_POLICIES;
                case RESPOND_TO_INQUIRIES -> Capability.RESPOND_INQUIRIES;
                case CONFIGURE_VENUE -> Capability.MANAGE_VENUE_MAPS;
                case MANAGE_INVENTORY -> Capability.EDIT_COMPANY_EVENTS;
            };
            caps.add(mapped);
        }
        return caps;
    }

    private static Set<Capability> unmodifiable(Set<Capability> src) {
        return Collections.unmodifiableSet(src);
    }
}
