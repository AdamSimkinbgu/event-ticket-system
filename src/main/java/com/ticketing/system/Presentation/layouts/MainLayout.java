package com.ticketing.system.Presentation.layouts;

import com.ticketing.system.Core.Application.dto.ActiveOrderDTO;
import com.ticketing.system.Core.Application.services.ReservationService;
import com.ticketing.system.Presentation.components.kit.LkAccountMenu;
import com.ticketing.system.Presentation.components.kit.LkMenu;
import com.ticketing.system.Presentation.components.kit.LkTopBar;
import com.ticketing.system.Presentation.security.Capabilities;
import com.ticketing.system.Presentation.security.Capability;
import com.ticketing.system.Presentation.security.SignOutFlow;
import com.ticketing.system.Presentation.session.AuthSession;
import com.ticketing.system.Presentation.session.GuestSession;
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
import com.vaadin.flow.server.VaadinSession;
import com.ticketing.system.Presentation.session.SessionIdentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Buyer-facing shell — composes {@link LkTopBar} with the tickethub
 * design system. Rebuilds the navbar on every navigation via
 * {@link AfterNavigationObserver} so the avatar menu reflects current
 * {@link AuthSession} state and the top-nav highlights the active section.
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

    private final ReservationService reservationService;
    private LkTopBar topBar;
    private final SignOutFlow signOutFlow;
    private final SessionIdentity identity;

    public MainLayout(ReservationService reservationService, SignOutFlow signOutFlow, SessionIdentity identity) {
        this.reservationService = reservationService;
        this.signOutFlow = signOutFlow;
        this.identity = identity;
        rebuildTopBar(null);
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        rebuildTopBar(findActiveLabel(event));
    }

    private void rebuildTopBar(String activeLabel) {
        if (topBar != null) topBar.getElement().removeFromParent();

        boolean signedIn = AuthSession.isSignedIn();
        String name = signedIn ? AuthSession.displayName() : "Guest";

        // Read cart state from the real service
                // Cart badge: cache for 10 seconds to avoid sync calls on every nav
        long now = System.currentTimeMillis();
        Long lastFetchMs = (Long) VaadinSession.getCurrent().getAttribute("cart.fetch.time");
        Integer cachedSize = (Integer) VaadinSession.getCurrent().getAttribute("cart.size");
        Long cachedDeadlineMs = (Long) VaadinSession.getCurrent().getAttribute("cart.deadline");
        
        int cartSize = 0;
        Long cartDeadlineMs = null;
        
        if (lastFetchMs != null && (now - lastFetchMs) < 10_000 && cachedSize != null) {
            // Use cached value
            cartSize = cachedSize;
            cartDeadlineMs = cachedDeadlineMs;
        } else {
            // Fetch fresh value
            try {
                String credential = identity.credential();
                ActiveOrderDTO order = (credential != null)
                        ? reservationService.viewMyActiveOrder(credential) : null;
                if (order != null && !order.lines().isEmpty()) {
                    cartSize = order.lines().size();
                    long remSec = order.remainingSecondsBeforeExpiry();
                    if (remSec > 0) {
                        cartDeadlineMs = System.currentTimeMillis() + remSec * 1000L;
                    }
                }
                // Cache the result
                VaadinSession.getCurrent().setAttribute("cart.size", cartSize);
                VaadinSession.getCurrent().setAttribute("cart.deadline", cartDeadlineMs);
                VaadinSession.getCurrent().setAttribute("cart.fetch.time", now);
            } catch (Exception ignored) { }
        }
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
            .cart(CartView.class, cartSize, cartDeadlineMs)
            .bellDefault(false);

        if (signedIn) {
            bar.account(initials(name), name, buildMemberMenu(name));
        } else {
            bar.guestActions(LoginView.class, RegisterView.class);
        }

        topBar = bar;
        addToNavbar(topBar);
    }

    /** Member-persona account menu — wired to {@link AuthSession} + view routes. */
    private LkAccountMenu buildMemberMenu(String name) {
        LkMenu menu = new LkMenu(
            new LkMenu.Item("ticket",    "My account").onClick(() -> UI.getCurrent().navigate(MyAccountView.class)),
            new LkMenu.Item("users",     "My profile").onClick(() -> UI.getCurrent().navigate(MyProfileView.class)),
            new LkMenu.Item("crown",     "My invitations").onClick(() -> UI.getCurrent().navigate(MyInvitationsView.class)),
            new LkMenu.Item("briefcase", "My companies").onClick(() -> UI.getCurrent().navigate(MyCompaniesView.class)),
            new LkMenu.Item("comment",   "Support inbox").onClick(() -> UI.getCurrent().navigate(SupportInboxView.class)),
            new LkMenu.Divider()
        );

        // Capability-driven owner workspace vs become-an-organizer CTA.
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
                signOutFlow.execute();
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
