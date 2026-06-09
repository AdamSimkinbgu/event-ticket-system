package com.ticketing.system.Presentation.views.auth;

import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.LkAuthCard;
import com.ticketing.system.Presentation.components.kit.LkCheckRow;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.security.MockAuth;
import com.ticketing.system.Presentation.views.admin.AdminDashboardView;
import com.ticketing.system.Presentation.views.catalog.BrowseEventsView;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route(value = "login", layout = MainLayout.class)
@PageTitle("Sign in · TicketHub")
@AnonymousAllowed
public class LoginView extends LkAuthCard {

    public LoginView() {
        super("Welcome back",
              "Sign in to buy tickets, manage events, and track your orders.");

        TextField username = new TextField("Username");
        username.setValue("adam");
        username.setRequired(true);
        username.setAutofocus(true);
        username.setWidthFull();

        PasswordField password = new PasswordField("Password");
        password.setValue("password123");
        password.setRequired(true);
        password.setWidthFull();

        LkCheckRow remember = new LkCheckRow("Remember me", false)
            .wrapperClass("auth-remember");

        Div rememberRow = new Div();
        rememberRow.getStyle()
            .set("display", "flex")
            .set("justify-content", "space-between")
            .set("align-items", "center");
        rememberRow.add(remember, forgotPasswordLink());

        Button signIn = new Button("Sign in", e -> attemptSignIn(username, password));
        signIn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        signIn.setWidthFull();
        signIn.addClickShortcut(Key.ENTER);

        col(14, username, password, rememberRow, signIn);
        divider("or continue with");

        Div social = new Div();
        social.getStyle().set("display", "flex").set("gap", "10px");
        social.add(socialBtn("Google"), socialBtn("Apple"));
        add(social);

        foot("Don't have an account?", "Create one →", RegisterView.class);
    }

    private Anchor forgotPasswordLink() {
        Anchor a = new Anchor("javascript:void(0)", "Forgot password?");
        a.addClassName("bz-link");
        return a;
    }

    private NativeButton socialBtn(String label) {
        NativeButton b = new NativeButton(label);
        b.addClassName("lk-btn");
        b.addClassName("lk-btn-secondary");
        b.addClassName("lk-btn-m");
        b.getStyle().set("flex", "1");
        return b;
    }

    private void attemptSignIn(TextField username, PasswordField password) {
        if (username.isEmpty() || password.isEmpty()) {
            Toasts.failure("Please enter both username and password.");
            return;
        }
        // Single sign-in surface for both pools — the username decides the route.
        // The pools are still disjoint (MockAuth.ADMIN_USERNAMES is the source of
        // truth) and RegisterView still refuses admin names.
        if (MockAuth.isAdminUsername(username.getValue())) {
            MockAuth.signInAsAdmin(username.getValue());
            Toasts.success("Signed in as admin · " + username.getValue());
            UI.getCurrent().navigate(AdminDashboardView.class);
        } else {
            MockAuth.signIn(username.getValue());
            Toasts.success("Signed in as " + username.getValue());
            UI.getCurrent().navigate(BrowseEventsView.class);
        }
    }
}
