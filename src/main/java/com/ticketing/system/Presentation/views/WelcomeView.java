package com.ticketing.system.Presentation.views;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Temporary welcome route for V2-F-01.
 *
 * Renders at {@code http://localhost:8080/} when the app boots and proves the
 * Vaadin Flow + Spring integration is wired correctly. The page exists for
 * smoke-test purposes only.
 *
 * <p><b>This will be deleted by V2-F-02</b>, which replaces it with the proper
 * {@code MainLayout} shell + the per-feature views from the rest of the V2
 * lanes (Browse, Cart, Checkout, etc.).
 */
@Route(value = "")
@PageTitle("Event Ticket Platform")
@AnonymousAllowed
public class WelcomeView extends VerticalLayout {

    public WelcomeView() {
        setSpacing(false);
        setPadding(true);
        setSizeFull();
        getStyle().set("text-align", "center");

        add(new H1("Event Ticket Platform"));
        add(new Paragraph("Vaadin Flow is alive. V2-F-02 will replace this with MainLayout."));
    }
}
