package com.ticketing.system.Presentation.security;

import com.ticketing.system.Presentation.session.MockCompanies;
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
 * <p>Two-step check on every navigation:
 * <ol>
 *   <li><b>Auth gate</b> — targets annotated {@link AnonymousAllowed}
 *       pass through. Otherwise, signed-out users are forwarded to
 *       {@link LoginView}.</li>
 *   <li><b>Owner gate</b> — targets implementing
 *       {@link RequiresOwnerCompany} require the signed-in user to own
 *       at least one company (per {@link MockCompanies#isOwner()}).
 *       Non-owners are forwarded to {@link CompanyRegistrationView} so
 *       they can register and become an owner.</li>
 * </ol>
 *
 * <p>Mock equivalent of Spring Security's filter chain + a method-level
 * permission check — replace with SecurityConfig + a custom
 * SecurityFilter once V2-AUTH-02 lands.
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

        // 1. Auth gate
        if (!target.isAnnotationPresent(AnonymousAllowed.class)) {
            if (!MockAuth.isSignedIn()) {
                event.forwardTo(LoginView.class);
                return;
            }
        }

        // 2. Owner gate — must own ≥ 1 company.
        if (RequiresOwnerCompany.class.isAssignableFrom(target) && !MockCompanies.isOwner()) {
            event.forwardTo(CompanyRegistrationView.class);
        }
    }
}
