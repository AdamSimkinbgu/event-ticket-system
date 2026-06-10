package com.ticketing.system.Presentation.layouts;

import com.ticketing.system.Presentation.components.kit.LkAccountMenu;
import com.ticketing.system.Presentation.components.kit.LkMenu;
import com.ticketing.system.Presentation.components.kit.LkTopBar;
import com.ticketing.system.Presentation.security.Capabilities;
import com.ticketing.system.Presentation.security.Capability;
import com.ticketing.system.Presentation.security.MockAuth;
import com.ticketing.system.Presentation.session.MockCart;
import com.ticketing.system.Presentation.views.admin.AdminDashboardView;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

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

        List<LkTopBar.NavItem> nav = new ArrayList<>();
        nav.add(new LkTopBar.NavItem("Browse",     BrowseEventsView.class));
        nav.add(new LkTopBar.NavItem("My Tickets", MyAccountView.class));
        // An admin who is also browsing as a member gets a fast-jump tab into
        // the platform shell. Driven by Capabilities so a dev-panel toggle
        // flips it on without rewiring routes.
        if (Capabilities.has(Capability.ADMIN_WORKSPACE)) {
            nav.add(new LkTopBar.NavItem("Admin", AdminDashboardView.class));
        }

        LkTopBar bar = new LkTopBar(LkTopBar.Variant.MAIN)
            .brand("TicketHub", LandingView.class)
            .nav(nav, activeLabel)
            .searchDefault()
            .cart(CartView.class, MockCart.size(), shortestCartDeadlineMs())
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
        // Capability-driven so swapping MockCompanies for a real service later is a
        // one-file change in Capabilities, not 30 view edits.
        if (Capabilities.has(Capability.OWNER_WORKSPACE)) {
            menu.add(new LkMenu.Item("building", "Workspace")
                .onClick(() -> UI.getCurrent().navigate(OwnerDashboardView.class)));
        } else if (Capabilities.has(Capability.REGISTER_COMPANY)) {
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

    /**
     * Earliest still-future deadline across the cart, in epoch millis —
     * the union of every line's {@code heldUntil} and the global checkout
     * deadline (set on entry to {@code CheckoutView}). The minimum wins,
     * so the navbar always reflects whichever timer expires first at this
     * moment. {@code null} when the cart is empty / every timer expired.
     */
    private static Long shortestCartDeadlineMs() {
        Instant now = Instant.now();
        return Stream.concat(
                MockCart.getItems().stream().map(MockCart.Item::heldUntil),
                Stream.of(MockCart.getCheckoutDeadline()))
            .filter(Objects::nonNull)
            .filter(t -> t.isAfter(now))
            .min(Comparator.naturalOrder())
            .map(Instant::toEpochMilli)
            .orElse(null);
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
