package com.ticketing.system.Presentation.views.admin;

import com.ticketing.system.Presentation.layouts.AdminLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "owner/sales", layout = AdminLayout.class)
@PageTitle("Company sales · Event Ticket Platform")
@PermitAll
public class CompanySalesView extends PlaceholderView {
    public CompanySalesView() {
        super(
            "Company sales history",
            "V2-VIEW-02",
            "Naim Elijah",
            "Owner-side view of their company's sales (UC-22). Calls CompanyManagementService.viewCompanySalesHistory. II.4.5.2 immutability is enforced by OrderReceipt being immutable. Closes #32, #117, #118."
        );
        add(wireFilterBar("Company (if multiple)", "Date range", "Event"));
        add(wireGrid("Date", "Event", "Buyer", "Tickets", "Total"));
        add(wireCard("Summary",
            wireRow(
                wireBox("Total revenue"),
                wireBox("Ticket count"),
                wireBox("Average order"),
                wireBox("Top event")
            )
        ));
    }
}
