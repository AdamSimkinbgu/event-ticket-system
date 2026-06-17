package com.ticketing.system.Presentation.views.auth;

import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.LkAuthCard;
import com.ticketing.system.Presentation.components.kit.LkCheckRow;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.presenters.auth.RegisterPresenter;
import com.ticketing.system.Presentation.session.AuthSession;
import com.ticketing.system.Presentation.session.GuestSession;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route(value = "register", layout = MainLayout.class)
@PageTitle("Register · TicketHub")
@AnonymousAllowed
public class RegisterView extends LkAuthCard {

    private final RegisterPresenter presenter;

    public RegisterView(RegisterPresenter presenter) {
        super("Create your account",
              "Join TicketHub to discover events and book in seconds.");
        this.presenter = presenter;

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

        IntegerField age = new IntegerField("Age");
        age.setMin(1);
        age.setMax(120);
        age.setStepButtonsVisible(true);
        age.setRequiredIndicatorVisible(true);
        age.setWidthFull();

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
            e -> attemptRegister(username, email, age, password, confirm));
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        create.setWidthFull();
        create.addClickShortcut(Key.ENTER);

        col(14, username, email, age, password, confirm, terms, create);
        foot("Already have an account?", "Sign in →", LoginView.class);
    }

    private void attemptRegister(TextField username, EmailField email, IntegerField age,
                                 PasswordField password, PasswordField confirm) {
        if (username.isEmpty() || email.isEmpty() || age.isEmpty()
                || password.isEmpty() || confirm.isEmpty()) {
            Toasts.failure("Please fill in every field.");
            return;
        }
        if (!password.getValue().equals(confirm.getValue())) {
            Toasts.failure("Passwords don't match.");
            return;
        }
        if (AuthSession.isAdminUsername(username.getValue())) {
            Toasts.failure("That username is reserved for the platform-admin pool.");
            return;
        }

        RegisterPresenter.Outcome outcome = presenter.attemptRegister(
            username.getValue(),
            email.getValue(),
            password.getValue(),
            age.getValue(),
            GuestSession.sessionId()
        );

        switch (outcome) {
            case RegisterPresenter.Outcome.Success ignored -> {
                Toasts.success("Welcome, " + username.getValue() + "! Account created — please sign in.");
                UI.getCurrent().navigate(LoginView.class);
            }
            case RegisterPresenter.Outcome.UsernameTaken ignored ->
                Toasts.failure("Username taken — please choose another.");
            case RegisterPresenter.Outcome.EmailTaken ignored ->
                Toasts.failure("That email is already registered.");
            case RegisterPresenter.Outcome.WeakPassword weak ->
                Toasts.failure("Weak password — " + weak.reason() + ".");
            case RegisterPresenter.Outcome.InvalidEmail ignored ->
                Toasts.failure("That email address looks invalid.");
            case RegisterPresenter.Outcome.GuestSessionMissing miss ->
                Toasts.failure("Session expired — please refresh the page. (" + miss.reason() + ")");
            case RegisterPresenter.Outcome.InvalidInput bad ->
                Toasts.failure("Invalid input: " + bad.reason());
            case RegisterPresenter.Outcome.Failure fail ->
                Toasts.failure("Registration failed: " + fail.reason());
        }
    }
}
