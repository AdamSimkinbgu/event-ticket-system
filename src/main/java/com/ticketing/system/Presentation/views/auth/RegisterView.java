package com.ticketing.system.Presentation.views.auth;

import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.LkAuthCard;
import com.ticketing.system.Presentation.components.kit.LkCheckRow;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.session.AuthSession;
import com.ticketing.system.Presentation.views.catalog.BrowseEventsView;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route(value = "register", layout = MainLayout.class)
@PageTitle("Register · TicketHub")
@AnonymousAllowed
public class RegisterView extends LkAuthCard {

    public RegisterView() {
        super("Create your account",
              "Join TicketHub to discover events and book in seconds.");

        TextField username = new TextField("Username");
        username.setPlaceholder("3–32 characters, letters and digits");
        username.setHelperText("This is how you'll appear to organizers.");
        username.setRequired(true);
        username.setWidthFull();
        username.setAutofocus(true);

        EmailField email = new EmailField("Email");
        email.setPlaceholder("you@email.com");
        email.setRequired(true);
        email.setWidthFull();

        PasswordField password = new PasswordField("Password");
        password.setPlaceholder("At least 8 characters");
        password.setRequired(true);
        password.setWidthFull();

        PasswordField confirm = new PasswordField("Confirm password");
        confirm.setPlaceholder("Re-enter your password");
        confirm.setRequired(true);
        confirm.setWidthFull();

        LkCheckRow terms = new LkCheckRow("I agree to the Terms and Privacy Policy", false)
            .wrapperClass("auth-remember");

        Button create = new Button("Create account",
            e -> attemptRegister(username, email, password, confirm));
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        create.setWidthFull();
        create.addClickShortcut(Key.ENTER);

        col(14, username, email, password, confirm, terms, create);
        foot("Already have an account?", "Sign in →", LoginView.class);
    }

    private void attemptRegister(TextField username, EmailField email,
                                 PasswordField password, PasswordField confirm) {
        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            Toasts.failure("Please fill in every field.");
            return;
        }
        if (!password.getValue().equals(confirm.getValue())) {
            Toasts.failure("Passwords don't match.");
            return;
        }
        if (password.getValue().length() < 8) {
            Toasts.failure("Password must be at least 8 characters.");
            return;
        }
        if (AuthSession.isAdminUsername(username.getValue())) {
            Toasts.failure("That username is reserved for the platform-admin pool.");
            return;
        }
        AuthSession.signIn(username.getValue());
        Toasts.success("Welcome, " + username.getValue() + "! Account created.");
        UI.getCurrent().navigate(BrowseEventsView.class);
    }
}
