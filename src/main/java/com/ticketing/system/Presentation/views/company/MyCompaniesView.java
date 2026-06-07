package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "my-companies", layout = MainLayout.class)
@PageTitle("My companies · Event Ticket Platform")
@PermitAll
public class MyCompaniesView extends PlaceholderView {
    public MyCompaniesView() {
        super(
            "My companies",
            "V2-CADMIN-05",
            "Abed Faour",
            "Owner dashboard — list of companies where the current user is founder / owner / manager. Calls CompanyManagementService.findCompaniesByUser. Entry point for owner-side actions."
        );
        add(wireGrid("Company", "Role", "Status", "Members", "Active events", "Actions"));
        add(wireBox("Per row: \"Open\" → details · \"Events\" → company event list · \"Managers\" → V2-CADMIN-03"));
        add(wireActions("Register a new company"));
    }
}
