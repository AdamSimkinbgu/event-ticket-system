package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.LkBanner;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkCol;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.presenters.company.CompanyRegistrationPresenter;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

/**
 * Register-a-company form — the entry point for a registered member to
 * become an organizer. Deliberately <i>not</i> gated by
 * {@link com.ticketing.system.Presentation.security.RequireCapability}
 * so non-owners can reach it as the workspace fallback destination.
 */
@Route(value = "register-company", layout = MainLayout.class)
@PageTitle("Register a company · TicketHub")
@PermitAll
public class CompanyRegistrationView extends LkPage {

    private final CompanyRegistrationPresenter presenter;

    private final TextField name = new TextField("Company name");
    private final TextArea description = new TextArea("Description");
    private final EmailField email = new EmailField("Contact email");

    public CompanyRegistrationView(CompanyRegistrationPresenter presenter) {
        this.presenter = presenter;
        title("Register a production company");
        subtitle("Found a new company — you become its immutable founder.");
        add(buildForm());
    }

    private Component buildForm() {
        Div narrow = new Div();
        narrow.addClassName("form-narrow");

        LkCard card = new LkCard("Company details").pad(20);

        name.setPlaceholder("e.g. BlueWave Productions");
        name.setRequired(true);
        name.setWidthFull();

        description.setPlaceholder("What kind of events does this company produce?");
        description.setMinHeight("120px");
        description.setWidthFull();

        email.setPlaceholder("contact@company.com");
        email.setRequired(true);
        email.setWidthFull();

        LkCol col = new LkCol().gap(14);
        col.add(name, description, email);
        card.add(col);

        narrow.add(card);
        narrow.add(new LkBanner(LkBanner.Tone.info, new LkIcon("info", 17),
                "The new company starts Active and you are recorded as the founder. You can invite owners and managers afterwards."));

        LkRow actions = new LkRow().gap(8).justify("flex-end");
        actions.add(
                new LkBtn("Cancel").variant(LkBtn.Variant.tertiary)
                        .onClick(e -> UI.getCurrent().navigate(MyCompaniesView.class)),
                new LkBtn("Register company").variant(LkBtn.Variant.primary)
                        .icon(new LkIcon("plus", 15))
                        .onClick(e -> attemptRegister()));
        narrow.add(actions);
        return narrow;
    }

    private void attemptRegister() {
        if (name.isEmpty()) {// mabey add email check
            Toasts.failure("Please fill in company name and contact email.");
            return;
        }

        String descriptionValue = description.getValue() == null ? "" : description.getValue().trim();
        if (descriptionValue.isEmpty()) {
            Toasts.failure("Please provide a company description.");
            return;
        }

        CompanyRegistrationPresenter.Outcome outcome = presenter.register(
                name.getValue().trim(),
                descriptionValue);

        switch (outcome) {
            case CompanyRegistrationPresenter.Outcome.Success success ->
                Toasts.success("'" + success.company().name() + "' registered — welcome to the organizer workspace.");
            case CompanyRegistrationPresenter.Outcome.NotAuthenticated ignored ->
                Toasts.failure("Sign in again to register a company.");
            case CompanyRegistrationPresenter.Outcome.InvalidInput invalid ->
                Toasts.failure(invalid.reason());
            case CompanyRegistrationPresenter.Outcome.NameTaken taken ->
                Toasts.failure(taken.reason());
            case CompanyRegistrationPresenter.Outcome.Failure fail ->
                Toasts.failure("Registration failed: " + fail.reason());
        }

        if (outcome instanceof CompanyRegistrationPresenter.Outcome.Success) {
            UI.getCurrent().navigate(OwnerDashboardView.class);
        }
    }
}
