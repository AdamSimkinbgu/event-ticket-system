package com.ticketing.system.Presentation.session;

import com.vaadin.flow.server.VaadinSession;

import java.util.ArrayList;
import java.util.List;

/**
 * Session-scoped placeholder for the current user's company memberships.
 *
 * <p>Used by {@code MainLayout} to decide whether to show "Owner
 * workspace" vs "Become an organizer" in the avatar menu, by
 * {@code MyCompaniesView} to render the list, by
 * {@code OwnerDashboardView} for the hub stats, and as a backing store for
 * {@code @RequireCapability(Capability.OWNER_WORKSPACE)}-gated workspace views.
 *
 * <p>Real implementation is V2-CADMIN-05 (Abed) — replaces with
 * {@code CompanyManagementService.findCompaniesByUser}.
 */
public final class MockCompanies {

    public record Company(
        String id,
        String name,
        String description,
        String contactEmail,
        String role,          // Founder / Co-owner / Manager
        String status,        // Active
        int    members,
        int    activeEvents
    ) { }

    private static final String KEY = "mockCompanies.list";

    private MockCompanies() { }

    @SuppressWarnings("unchecked")
    public static List<Company> forCurrentUser() {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) return new ArrayList<>();
        List<Company> list = (List<Company>) s.getAttribute(KEY);
        if (list == null) {
            list = new ArrayList<>();
            s.setAttribute(KEY, list);
        }
        return list;
    }

    public static boolean isOwner() { return !forCurrentUser().isEmpty(); }
    public static int     size()    { return forCurrentUser().size(); }

    public static void add(Company c)    { forCurrentUser().add(c); }
    public static void clear()           { forCurrentUser().clear(); }
}
