package com.ticketing.system.Presentation.security;

import com.ticketing.system.Presentation.views.auth.LoginView;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.springframework.stereotype.Component;

/**
 * Global navigation guard for the V2 placeholder UI.
 *
 * <p>Picked up automatically by Vaadin's Spring integration (any
 * {@link VaadinServiceInitListener} bean is registered on the service).
 * Adds a {@code BeforeEnterListener} that on every navigation lets the
 * target through if it carries {@link AnonymousAllowed}; otherwise, if
 * {@link MockAuth#isSignedIn()} is false, forwards to {@link LoginView}.
 *
 * <p>The guest-accessible surface is therefore declared per-view via the
 * annotation (Browse, EventDetails, Cart, Checkout, OrderConfirmation,
 * Login, Register all use it). Anything else demands a signed-in session.
 *
 * <p>Mock equivalent of Spring Security's filter chain — replace with
 * SecurityConfig when V2-AUTH-02 lands.
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
        if (target.isAnnotationPresent(AnonymousAllowed.class)) return;
        if (!MockAuth.isSignedIn()) {
            event.forwardTo(LoginView.class);
        }
    }
}
