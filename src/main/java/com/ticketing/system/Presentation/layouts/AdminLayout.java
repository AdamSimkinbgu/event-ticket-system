package com.ticketing.system.Presentation.layouts;

import com.ticketing.system.Presentation.components.kit.LkAccountMenu;
import com.ticketing.system.Presentation.components.kit.LkMenu;
import com.ticketing.system.Presentation.components.kit.LkSideNav;
import com.ticketing.system.Presentation.components.kit.LkTopBar;
import com.ticketing.system.Presentation.security.MockAuth;
import com.ticketing.system.Presentation.views.account.MyAccountView;
import com.ticketing.system.Presentation.views.account.MyInvitationsView;
import com.ticketing.system.Presentation.views.account.MyProfileView;
import com.ticketing.system.Presentation.views.admin.CompanySalesView;
import com.ticketing.system.Presentation.views.auth.LoginView;
import com.ticketing.system.Presentation.views.catalog.BrowseEventsView;
import com.ticketing.system.Presentation.views.company.CompanyEventListView;
import com.ticketing.system.Presentation.views.company.CompanyInquiryInboxView;
import com.ticketing.system.Presentation.views.company.ManagerListView;
import com.ticketing.system.Presentation.views.company.MyCompaniesView;
import com.ticketing.system.Presentation.views.company.OwnerAppointmentView;
import com.ticketing.system.Presentation.views.company.OwnerDashboardView;
import com.ticketing.system.Presentation.views.company.PurchasePolicyEditorView;
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
 * Owner workspace shell — main blue topbar with a " › Workspace" crumb
 * and a single-section left drawer of owner-only items. The system-admin
 * shell is a separate layout ({@code PlatformAdminLayout}) reached only
 * through its own sign-in endpoint, so owners never see admin nav and
 * vice versa.
 */
public class AdminLayout extends AppLayout implements AfterNavigationObserver {

    private static final Map<Class<?>, String> OWNER_LABELS = Map.of(
        OwnerDashboardView.class,       "Owner workspace",
        MyCompaniesView.class,          "My Companies",
        CompanyEventListView.class,     "My Events",
        CompanyInquiryInboxView.class,  "Inquiries",
        CompanySalesView.class,         "Company Sales",
        ManagerListView.class,          "Managers",
        OwnerAppointmentView.class,     "Appoint Co-owner",
        PurchasePolicyEditorView.class, "Purchase Policies"
    );

    private LkTopBar topBar;
    private LkSideNav ownerNav;

    public AdminLayout() {
        rebuildTopBar();
        buildDrawerOnce();
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        rebuildTopBar();
        ownerNav.setActive(findLabel(event, OWNER_LABELS));
    }

    private void rebuildTopBar() {
        if (topBar != null) topBar.getElement().removeFromParent();
        String name = MockAuth.isSignedIn() ? MockAuth.displayName() : "Owner";
        topBar = new LkTopBar(LkTopBar.Variant.MAIN)
            .brand("TicketHub", " › Workspace", LandingView.class)
            .nav(List.of(
                new LkTopBar.NavItem("Browse",     BrowseEventsView.class),
                new LkTopBar.NavItem("My Tickets", MyAccountView.class)
            ), null)
            .bellDefault(false)
            .account(initials(name), name, buildOwnerMenu(name));
        addToNavbar(topBar);
    }

    private void buildDrawerOnce() {
        ownerNav = new LkSideNav("Owner").items(List.of(
            new LkSideNav.Item("building",  "Owner workspace",   OwnerDashboardView.class),
            new LkSideNav.Item("briefcase", "My Companies",      MyCompaniesView.class),
            new LkSideNav.Item("calendar",  "My Events",         CompanyEventListView.class),
            new LkSideNav.Item("comment",   "Inquiries",         CompanyInquiryInboxView.class),
            new LkSideNav.Item("chart",     "Company Sales",     CompanySalesView.class),
            new LkSideNav.Item("users",     "Managers",          ManagerListView.class),
            new LkSideNav.Item("crown",     "Appoint Co-owner",  OwnerAppointmentView.class),
            new LkSideNav.Item("policy",    "Purchase Policies", PurchasePolicyEditorView.class)
        ), null);

        Div drawer = new Div();
        drawer.add(ownerNav);
        addToDrawer(drawer);
    }

    private LkAccountMenu buildOwnerMenu(String name) {
        LkMenu menu = new LkMenu(
            new LkMenu.Item("ticket",    "My account").onClick(() -> UI.getCurrent().navigate(MyAccountView.class)),
            new LkMenu.Item("users",     "My profile").onClick(() -> UI.getCurrent().navigate(MyProfileView.class)),
            new LkMenu.Item("crown",     "My invitations").onClick(() -> UI.getCurrent().navigate(MyInvitationsView.class)),
            new LkMenu.Item("briefcase", "My companies").onClick(() -> UI.getCurrent().navigate(MyCompaniesView.class)),
            new LkMenu.Divider(),
            new LkMenu.Item("arrowLeft", "Back to buyer site").onClick(() -> UI.getCurrent().navigate(BrowseEventsView.class)),
            new LkMenu.Divider(),
            new LkMenu.Item("logout",    "Sign out").danger().onClick(() -> {
                MockAuth.signOut();
                UI.getCurrent().navigate(LoginView.class);
            })
        );
        return new LkAccountMenu(initials(name), name, "Owner · Workspace", menu, null, null);
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
