package com.ticketing.system.Presentation.security;

import com.ticketing.system.Presentation.session.AuthSession;
import com.ticketing.system.Presentation.views.auth.LoginView;
import com.ticketing.system.Presentation.views.company.CompanyRegistrationView;
import com.ticketing.system.Presentation.views.company.MyCompaniesView;
import com.ticketing.system.Presentation.views.company.OwnerDashboardView;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Global navigation guard for the V2 placeholder UI.
 *
 * <p>Two-step check on every navigation:
 * <ol>
 *   <li><b>Auth gate</b> — targets not annotated {@link AnonymousAllowed}
 *       require a signed-in user. Signed-out users are forwarded to
 *       the unified {@link LoginView}, which decides admin vs member by
 *       username after auth.</li>
 *   <li><b>Capability gate</b> — targets annotated
 *       {@link RequireCapability} require the user to hold the named
 *       capability. The {@code fallbackFor} table picks the right
 *       "you can't go here, try this instead" destination based on the
 *       missing capability:
 *       <ul>
 *         <li>admin capabilities → {@link LoginView}</li>
 *         <li>owner workspace caps when the user has no company →
 *             {@link CompanyRegistrationView} (so they can become one)</li>
 *         <li>owner workspace caps when the user already has a company
 *             but lacks this specific permission → {@link OwnerDashboardView}</li>
 *         <li>everything else → {@link MyCompaniesView}</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p>Replaces the earlier marker-interface system. {@link Capability}
 * subsumes the two role boundaries those markers used to enforce.
 */
@Component
public class AuthBootstrap implements VaadinServiceInitListener {

    /** Capabilities that belong to the system-admin pool. */
    private static final Set<Capability> ADMIN_CAPS = EnumSet.of(
        Capability.ADMIN_WORKSPACE,
        Capability.VIEW_GLOBAL_HISTORY,
        Capability.BROADCAST_ANNOUNCEMENT,
        Capability.MANAGE_COMPLAINTS,
        Capability.VIEW_ORG_TREES,
        Capability.ADMIN_SETTINGS
    );

    /** Capabilities that imply company membership ("owner workspace"). */
    private static final Set<Capability> WORKSPACE_CAPS = EnumSet.of(
        Capability.OWNER_WORKSPACE,
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
        Capability.CANCEL_EVENT,
        Capability.DISSOLVE_COMPANY,
        Capability.TRANSFER_FOUNDERSHIP
    );

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiInit ->
            uiInit.getUI().addBeforeEnterListener(this::guard));
    }

    private void guard(BeforeEnterEvent event) {
        Class<?> target = event.getNavigationTarget();
        RequireCapability cap = target.getAnnotation(RequireCapability.class);

        // 1. Auth gate — every non-anonymous view needs a session. The unified
        // LoginView is the single sign-in surface; if the user types an admin
        // username, it auto-routes them to AdminDashboardView after auth.
        if (!target.isAnnotationPresent(AnonymousAllowed.class)) {
            if (!AuthSession.isSignedIn()) {
                event.forwardTo(LoginView.class);
                return;
            }
        }

        // 2. Capability gate.
        if (cap != null && !Capabilities.has(cap.value())) {
            event.forwardTo(fallbackFor(cap.value()));
        }
    }

    /** Where to send a user who lacks the named capability. */
    private static Class<? extends com.vaadin.flow.component.Component> fallbackFor(Capability c) {
        if (ADMIN_CAPS.contains(c)) {
            // Signed-in member trying to reach the admin shell — bounce to
            // the unified sign-in. Re-authentication as an admin will land
            // them on the platform shell.
            return LoginView.class;
        }
        if (WORKSPACE_CAPS.contains(c)) {
            // No company yet → become one. Otherwise the user has a
            // company but lacks this specific permission inside it, so
            // park them on the workspace home where they can see what
            // they CAN do.
            return Capabilities.has(Capability.OWNER_WORKSPACE)
                ? OwnerDashboardView.class
                : CompanyRegistrationView.class;
        }
        return MyCompaniesView.class;
    }
}
