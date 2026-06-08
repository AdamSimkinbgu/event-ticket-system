package com.ticketing.system.Presentation.layouts;

import com.ticketing.system.Presentation.components.kit.LkAccountMenu;
import com.ticketing.system.Presentation.components.kit.LkMenu;
import com.ticketing.system.Presentation.components.kit.LkTopBar;
import com.ticketing.system.Presentation.security.MockAuth;
import com.ticketing.system.Presentation.session.MockCompanies;
import com.ticketing.system.Presentation.views.company.CompanyRegistrationView;
import com.ticketing.system.Presentation.views.account.MyAccountView;
import com.ticketing.system.Presentation.views.account.MyInvitationsView;
import com.ticketing.system.Presentation.views.account.MyProfileView;
import com.ticketing.system.Presentation.views.account.SupportInboxView;
import com.ticketing.system.Presentation.views.auth.LoginView;
import com.ticketing.system.Presentation.views.auth.RegisterView;
import com.ticketing.system.Presentation.views.catalog.BrowseEventsView;
import com.ticketing.system.Presentation.views.catalog.EventDetailsView;
import com.ticketing.system.Presentation.views.landing.LandingView;
import com.ticketing.system.Presentation.views.company.MyCompaniesView;
import com.ticketing.system.Presentation.views.company.OwnerDashboardView;
import com.ticketing.system.Presentation.views.order.CartView;
import com.ticketing.system.Presentation.views.order.CheckoutView;
import com.ticketing.system.Presentation.views.order.OrderConfirmationView;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;

import java.util.List;
import java.util.Map;

/**
 * Buyer-facing shell — composes {@link LkTopBar} with the tickethub
 * design system. Rebuilds the navbar on every navigation via
 * {@link AfterNavigationObserver} so the avatar menu reflects current
 * {@link MockAuth} state and the top-nav highlights the active section.
 *
 * <p>System-admin entry points are deliberately not exposed here — the
 * admin workspace lives at its own endpoint behind a separate sign-in.
 */
public class MainLayout extends AppLayout implements AfterNavigationObserver {

    /** View class → top-nav label that should light up while that view is active. */
    private static final Map<Class<?>, String> NAV_LABELS = Map.of(
        BrowseEventsView.class,      "Browse",
        EventDetailsView.class,      "Browse",
        MyAccountView.class,         "My Tickets",
        CartView.class,              "My Tickets",
        CheckoutView.class,          "My Tickets",
        OrderConfirmationView.class, "My Tickets"
    );

    private LkTopBar topBar;

    public MainLayout() {
        rebuildTopBar(null);
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        rebuildTopBar(findActiveLabel(event));
    }

    private void rebuildTopBar(String activeLabel) {
        if (topBar != null) topBar.getElement().removeFromParent();

        boolean signedIn = MockAuth.isSignedIn();
        String name = signedIn ? MockAuth.displayName() : "Guest";

        LkTopBar bar = new LkTopBar(LkTopBar.Variant.MAIN)
            .brand("TicketHub", LandingView.class)
            .nav(List.of(
                new LkTopBar.NavItem("Browse",     BrowseEventsView.class),
                new LkTopBar.NavItem("My Tickets", MyAccountView.class)
            ), activeLabel)
            .searchDefault()
            .bellDefault(false);

        if (signedIn) {
            bar.account(initials(name), name, buildMemberMenu(name));
        } else {
            bar.guestActions(LoginView.class, RegisterView.class);
        }

        topBar = bar;
        addToNavbar(topBar);
    }

    /** Member-persona account menu — wired to {@link MockAuth} + view routes. */
    private LkAccountMenu buildMemberMenu(String name) {
        LkMenu menu = new LkMenu(
            new LkMenu.Item("ticket",    "My account").onClick(() -> UI.getCurrent().navigate(MyAccountView.class)),
            new LkMenu.Item("users",     "My profile").onClick(() -> UI.getCurrent().navigate(MyProfileView.class)),
            new LkMenu.Item("crown",     "My invitations").onClick(() -> UI.getCurrent().navigate(MyInvitationsView.class)),
            new LkMenu.Item("briefcase", "My companies").onClick(() -> UI.getCurrent().navigate(MyCompaniesView.class)),
            new LkMenu.Item("comment",   "Support inbox").onClick(() -> UI.getCurrent().navigate(SupportInboxView.class)),
            new LkMenu.Divider()
        );

        // Conditional: owner workspace vs. become-an-organizer CTA.
        if (MockCompanies.isOwner()) {
            menu.add(new LkMenu.Item("building", "Owner workspace")
                .onClick(() -> UI.getCurrent().navigate(OwnerDashboardView.class)));
        } else {
            menu.add(new LkMenu.Item("plus", "Become an organizer")
                .hint("free")
                .onClick(() -> UI.getCurrent().navigate(CompanyRegistrationView.class)));
        }

        menu.add(
            new LkMenu.Divider(),
            new LkMenu.Item("logout", "Sign out").danger().onClick(() -> {
                MockAuth.signOut();
                UI.getCurrent().navigate(LoginView.class);
            })
        );
        return new LkAccountMenu(initials(name), name, "Signed in member · V2-AUTH-02 stub", menu, null, null);
    }

    private static String findActiveLabel(AfterNavigationEvent event) {
        for (HasElement el : event.getActiveChain()) {
            String label = NAV_LABELS.get(el.getClass());
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
