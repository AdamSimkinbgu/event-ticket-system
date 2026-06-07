package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "owner/register-company", layout = MainLayout.class)
@PageTitle("Register a company · Event Ticket Platform")
@PermitAll
public class CompanyRegistrationView extends PlaceholderView {
    public CompanyRegistrationView() {
        super(
            "Register a production company",
            "V2-COMP-REG-01",
            "Bentzion Hadad",
            "Form for UC-17 / UC-18. Calls CompanyManagementService.registerCompany. Founder becomes the immutable founderId; the new company starts ACTIVE. Closes #28, II.3.2.x."
        );
        add(wireCard("Company details",
            wireForm("Company name (required)", "Description (TextArea)", "Contact email (EmailValidator)")
        ));
        add(wireActions("Register", "Cancel"));
        add(wireBox("Success → /my-companies (the new entry appears)"));
    }
}
