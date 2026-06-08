package com.ticketing.system.Presentation.layouts;

import com.ticketing.system.Presentation.components.kit.LkAccountMenu;
import com.ticketing.system.Presentation.components.kit.LkMenu;
import com.ticketing.system.Presentation.components.kit.LkSideNav;
import com.ticketing.system.Presentation.components.kit.LkTopBar;
import com.ticketing.system.Presentation.security.MockAuth;
import com.ticketing.system.Presentation.views.admin.AdminAnnouncementsView;
import com.ticketing.system.Presentation.views.admin.AdminComplaintQueueView;
import com.ticketing.system.Presentation.views.admin.AdminDashboardView;
import com.ticketing.system.Presentation.views.admin.AdminLoginView;
import com.ticketing.system.Presentation.views.admin.GlobalHistoryView;
import com.ticketing.system.Presentation.views.admin.OrganizationalTreeView;
import com.ticketing.system.Presentation.views.landing.LandingView;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;

import java.util.List;
import java.util.Map;

/**
 * Platform-admin shell — orange "Event Ticket Platform · Admin" top bar
 * with a single-section drawer of admin-only items. Unreachable except
 * through {@link AdminLoginView} (gated by {@link com.ticketing.system.Presentation.security.RequiresAdminRole}),
 * which keeps the system-admin workspace hidden from members.
 */
public class PlatformAdminLayout extends AppLayout implements AfterNavigationObserver {

    private static final Map<Class<?>, String> ADMIN_LABELS = Map.of(
        AdminDashboardView.class,      "Admin workspace",
        GlobalHistoryView.class,       "Global History",
        OrganizationalTreeView.class,  "Organizational Tree",
        AdminAnnouncementsView.class,  "Announcements",
        AdminComplaintQueueView.class, "Complaint Queue"
    );

    private LkTopBar topBar;
    private LkSideNav adminNav;

    public PlatformAdminLayout() {
        rebuildTopBar();
        buildDrawerOnce();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        rebuildTopBar();
        adminNav.setActive(findLabel(event));
    }

    private void rebuildTopBar() {
        if (topBar != null) topBar.getElement().removeFromParent();
        String name = MockAuth.isAdmin() ? MockAuth.displayName() : "Admin";
        topBar = new LkTopBar(LkTopBar.Variant.PLATFORM)
            .brand("Event Ticket Platform", " · Admin", LandingView.class)
            .rightLink("Back to site", "arrowLeft", LandingView.class)
            .bellDefault(true)
            .account(initials(name), name, buildAdminMenu(name), "#fff", "#c2410c");
        addToNavbar(topBar);
    }

    private void buildDrawerOnce() {
        adminNav = new LkSideNav("Admin").items(List.of(
            new LkSideNav.Item("building", "Admin workspace",     AdminDashboardView.class),
            new LkSideNav.Item("chart",    "Global History",      GlobalHistoryView.class),
            new LkSideNav.Item("org",      "Organizational Tree", OrganizationalTreeView.class),
            new LkSideNav.Item("comment",  "Announcements",       AdminAnnouncementsView.class),
            new LkSideNav.Item("warning",  "Complaint Queue",     AdminComplaintQueueView.class)
        ), null).platform();

        Div drawer = new Div();
        drawer.add(adminNav);
        addToDrawer(drawer);
    }

    private LkAccountMenu buildAdminMenu(String name) {
        LkMenu menu = new LkMenu(
            new LkMenu.Item("gear",      "Admin settings"),
            new LkMenu.Item("chart",     "Platform analytics"),
            new LkMenu.Divider(),
            new LkMenu.Item("arrowLeft", "Back to site").onClick(() -> UI.getCurrent().navigate(LandingView.class)),
            new LkMenu.Divider(),
            new LkMenu.Item("logout",    "Sign out").danger().onClick(() -> {
                MockAuth.signOut();
                UI.getCurrent().navigate(AdminLoginView.class);
            })
        );
        return new LkAccountMenu(initials(name), name, "System administrator", menu, "#fff", "#c2410c");
    }

    private String findLabel(AfterNavigationEvent event) {
        for (HasElement el : event.getActiveChain()) {
            String label = ADMIN_LABELS.get(el.getClass());
            if (label != null) return label;
        }
        return null;
    }

    private static String initials(String name) {
        if (name == null || name.isBlank()) return "??";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            String p = parts[0];
            return p.substring(0, Math.min(2, p.length())).toUpperCase();
        }
        return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
    }
}
