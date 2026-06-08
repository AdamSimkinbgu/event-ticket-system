package com.ticketing.system.Presentation.views.admin;

import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.LkBanner;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.security.MockAuth;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Standalone admin sign-in endpoint at {@code /admin/sign-in}. The only
 * way into the platform-admin shell — no layout, no avatar menu link,
 * no in-product link. Submitting calls {@link MockAuth#signInAsAdmin}
 * and forwards to {@link AdminDashboardView}.
 */
@Route("admin/sign-in")
@PageTitle("Admin sign in · Event Ticket Platform")
@AnonymousAllowed
public class AdminLoginView extends Div {

    public AdminLoginView() {
        addClassName("auth-wrap");

        Div card = new Div();
        card.addClassName("auth-card");
        card.addClassName("auth-card-admin");
        card.getStyle().set("border-top", "3px solid var(--lk-orange-600, #c2410c)");

        card.add(brandLine());

        H2 title = new H2("Platform admin sign in");
        title.addClassName("auth-title");
        card.add(title);

        Paragraph sub = new Paragraph(
            "This entrance is reserved for system administrators. Member " +
            "accounts cannot sign in here — use the main sign-in instead."
        );
        sub.addClassName("auth-sub");
        card.add(sub);

        TextField username = new TextField("Username");
        username.setValue("admin");
        username.setRequired(true);
        username.setAutofocus(true);
        username.setWidthFull();

        PasswordField password = new PasswordField("Password");
        password.setValue("admin123");
        password.setRequired(true);
        password.setWidthFull();

        Button signIn = new Button("Sign in to admin", e -> attemptSignIn(username, password));
        signIn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        signIn.setWidthFull();
        signIn.getStyle()
            .set("background", "var(--lk-orange-600, #c2410c)")
            .set("color", "#fff");
        signIn.addClickShortcut(Key.ENTER);

        Div col = new Div();
        col.getStyle()
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("gap", "14px");
        col.add(username, password, signIn);
        card.add(col);

        card.add(new LkBanner(LkBanner.Tone.warn, new LkIcon("warning", 17),
            "Admin actions are audited. Unauthorized access is logged."));

        Div foot = new Div();
        foot.getStyle()
            .set("margin-top", "18px")
            .set("display", "flex")
            .set("justify-content", "center")
            .set("font-size", "13px");
        Anchor back = new Anchor("/", "← Back to TicketHub");
        back.addClassName("bz-link");
        foot.add(back);
        card.add(foot);

        add(card);
    }

    private Component brandLine() {
        Div brand = new Div();
        brand.addClassName("auth-brand");
        brand.add(new LkIcon("building", 24));
        Span name = new Span();
        name.getElement().setProperty("innerHTML",
            "<b>Event Ticket Platform</b><span style='color:var(--lk-muted, #64748b)'> · Admin</span>");
        brand.add(name);
        return brand;
    }

    private void attemptSignIn(TextField username, PasswordField password) {
        if (username.isEmpty() || password.isEmpty()) {
            Toasts.failure("Please enter both username and password.");
            return;
        }
        if (!MockAuth.isAdminUsername(username.getValue())) {
            Toasts.failure("That isn't a platform-admin account. Use the main sign-in instead.");
            return;
        }
        MockAuth.signInAsAdmin(username.getValue());
        Toasts.success("Signed in as admin · " + username.getValue());
        UI.getCurrent().navigate(AdminDashboardView.class);
    }
}
