package com.ticketing.system.Presentation.security;

import com.ticketing.system.Presentation.session.MockCompanies;
import com.ticketing.system.Presentation.views.admin.AdminLoginView;
import com.ticketing.system.Presentation.views.auth.LoginView;
import com.ticketing.system.Presentation.views.company.CompanyRegistrationView;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.springframework.stereotype.Component;

/**
 * Global navigation guard for the V2 placeholder UI.
 *
 * <p>Three-step check on every navigation:
 * <ol>
 *   <li><b>Admin gate</b> — targets implementing
 *       {@link RequiresAdminRole} require a signed-in admin
 *       ({@link MockAuth#isAdmin()}). Non-admins are forwarded to
 *       {@link AdminLoginView} so the system-admin workspace is
 *       unreachable from a member's session.</li>
 *   <li><b>Auth gate</b> — targets not annotated {@link AnonymousAllowed}
 *       require any signed-in user. Signed-out users are forwarded to
 *       {@link LoginView}.</li>
 *   <li><b>Owner gate</b> — targets implementing
 *       {@link RequiresOwnerCompany} require the signed-in user to own
 *       at least one company. Non-owners are forwarded to
 *       {@link CompanyRegistrationView}.</li>
 * </ol>
 *
 * <p>Mock equivalent of Spring Security's filter chain + method-level
 * permission checks — replace once V2-AUTH-02 lands.
 */
@Component
public class AuthBootstrap implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiInit ->
            uiInit.getUI().addBeforeEnterListener(this::guard));
    }

    private void guard(BeforeEnterEvent event) {
        Class<?> target = event.getNavigationTarget();

        // 1. Admin gate — has its own dedicated sign-in endpoint.
        if (RequiresAdminRole.class.isAssignableFrom(target) && !MockAuth.isAdmin()) {
            event.forwardTo(AdminLoginView.class);
            return;
        }

        // 2. Auth gate.
        if (!target.isAnnotationPresent(AnonymousAllowed.class)) {
            if (!MockAuth.isSignedIn()) {
                event.forwardTo(LoginView.class);
                return;
            }
        }

        // 3. Owner gate — must own ≥ 1 company.
        if (RequiresOwnerCompany.class.isAssignableFrom(target) && !MockCompanies.isOwner()) {
            event.forwardTo(CompanyRegistrationView.class);
        }
    }
}
