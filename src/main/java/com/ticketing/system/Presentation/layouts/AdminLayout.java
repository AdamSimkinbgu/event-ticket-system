package com.ticketing.system.Presentation.layouts;

import com.ticketing.system.Presentation.components.kit.LkAccountMenu;
import com.ticketing.system.Presentation.components.kit.LkMenu;
import com.ticketing.system.Presentation.components.kit.LkSideNav;
import com.ticketing.system.Presentation.components.kit.LkTopBar;
import com.ticketing.system.Presentation.security.MockAuth;
import com.ticketing.system.Presentation.views.admin.AdminAnnouncementsView;
import com.ticketing.system.Presentation.views.admin.AdminComplaintQueueView;
import com.ticketing.system.Presentation.views.admin.AdminDashboardView;
import com.ticketing.system.Presentation.views.admin.CompanySalesView;
import com.ticketing.system.Presentation.views.admin.GlobalHistoryView;
import com.ticketing.system.Presentation.views.admin.OrganizationalTreeView;
import com.ticketing.system.Presentation.views.auth.LoginView;
import com.ticketing.system.Presentation.views.catalog.BrowseEventsView;
import com.ticketing.system.Presentation.views.company.CompanyEventListView;
import com.ticketing.system.Presentation.views.company.CompanyInquiryInboxView;
import com.ticketing.system.Presentation.views.company.CompanyRegistrationView;
import com.ticketing.system.Presentation.views.company.ManagerListView;
import com.ticketing.system.Presentation.views.company.OwnerAppointmentView;
import com.ticketing.system.Presentation.views.company.OwnerDashboardView;
import com.ticketing.system.Presentation.views.company.PurchasePolicyEditorView;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;

import java.util.List;
import java.util.Map;

/**
 * Organizer + system-admin shell — orange "platform" top bar with a
 * two-section left drawer (Owner items / Admin items). Rebuilds the
 * top bar on every navigation so the account menu reflects current
 * {@link MockAuth} state, and toggles the drawer's active highlight
 * via {@link LkSideNav#setActive(String)} without rebuilding the items.
 */
public class AdminLayout extends AppLayout implements AfterNavigationObserver {

    private static final Map<Class<?>, String> OWNER_LABELS = Map.of(
        OwnerDashboardView.class,       "Owner workspace",
        CompanyEventListView.class,     "My Events",
        CompanyInquiryInboxView.class,  "Inquiries",
        CompanySalesView.class,         "Company Sales",
        ManagerListView.class,          "Managers",
        OwnerAppointmentView.class,     "Appoint Co-owner",
        PurchasePolicyEditorView.class, "Purchase Policies",
        CompanyRegistrationView.class,  "Register Company"
    );

    private static final Map<Class<?>, String> ADMIN_LABELS = Map.of(
        AdminDashboardView.class,      "Admin workspace",
        GlobalHistoryView.class,       "Global History",
        OrganizationalTreeView.class,  "Organizational Tree",
        AdminAnnouncementsView.class,  "Announcements",
        AdminComplaintQueueView.class, "Complaint Queue"
    );

    private LkTopBar topBar;
    private LkSideNav ownerNav;
    private LkSideNav adminNav;

    public AdminLayout() {
        rebuildTopBar();
        buildDrawerOnce();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        rebuildTopBar();
        ownerNav.setActive(findLabel(event, OWNER_LABELS));
        adminNav.setActive(findLabel(event, ADMIN_LABELS));
    }

    private void rebuildTopBar() {
        if (topBar != null) topBar.getElement().removeFromParent();
        String name = MockAuth.isSignedIn() ? MockAuth.displayName() : "Admin";
        topBar = new LkTopBar(LkTopBar.Variant.PLATFORM)
            .brand("Event Ticket Platform", " · Admin")
            .rightLink("Back to site", "arrowLeft", BrowseEventsView.class)
            .bellDefault(true)
            .account(initials(name), name, buildAdminMenu(name), "#fff", "#c2410c");
        addToNavbar(topBar);
    }

    private void buildDrawerOnce() {
        ownerNav = new LkSideNav("Owner").items(List.of(
            new LkSideNav.Item("building",  "Owner workspace",   OwnerDashboardView.class),
            new LkSideNav.Item("calendar",  "My Events",         CompanyEventListView.class),
            new LkSideNav.Item("comment",   "Inquiries",         CompanyInquiryInboxView.class),
            new LkSideNav.Item("chart",     "Company Sales",     CompanySalesView.class),
            new LkSideNav.Item("users",     "Managers",          ManagerListView.class),
            new LkSideNav.Item("crown",     "Appoint Co-owner",  OwnerAppointmentView.class),
            new LkSideNav.Item("policy",    "Purchase Policies", PurchasePolicyEditorView.class),
            new LkSideNav.Item("briefcase", "Register Company",  CompanyRegistrationView.class)
        ), null).platform();

        adminNav = new LkSideNav("Admin").items(List.of(
            new LkSideNav.Item("building", "Admin workspace",     AdminDashboardView.class),
            new LkSideNav.Item("chart",    "Global History",      GlobalHistoryView.class),
            new LkSideNav.Item("org",      "Organizational Tree", OrganizationalTreeView.class),
            new LkSideNav.Item("comment",  "Announcements",       AdminAnnouncementsView.class),
            new LkSideNav.Item("warning",  "Complaint Queue",     AdminComplaintQueueView.class)
        ), null).platform();

        Div drawer = new Div();
        drawer.add(ownerNav, adminNav);
        addToDrawer(drawer);
    }

    private LkAccountMenu buildAdminMenu(String name) {
        LkMenu menu = new LkMenu(
            new LkMenu.Item("gear",      "Admin settings"),
            new LkMenu.Item("chart",     "Platform analytics"),
            new LkMenu.Divider(),
            new LkMenu.Item("arrowLeft", "Back to buyer site").onClick(() -> UI.getCurrent().navigate(BrowseEventsView.class)),
            new LkMenu.Divider(),
            new LkMenu.Item("logout",    "Sign out").danger().onClick(() -> {
                MockAuth.signOut();
                UI.getCurrent().navigate(LoginView.class);
            })
        );
        return new LkAccountMenu(initials(name), name, "System administrator", menu, "#fff", "#c2410c");
    }

    private static String findLabel(AfterNavigationEvent event, Map<Class<?>, String> labels) {
        for (HasElement el : event.getActiveChain()) {
            String label = labels.get(el.getClass());
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
