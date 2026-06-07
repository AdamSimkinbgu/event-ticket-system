package com.ticketing.system.Presentation.views.admin;

import com.ticketing.system.Presentation.layouts.AdminLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route(value = "admin/global-history", layout = AdminLayout.class)
@PageTitle("Global purchase history · Admin")
@RolesAllowed("ADMIN")
public class GlobalHistoryView extends PlaceholderView {
    public GlobalHistoryView() {
        super(
            "Global purchase history",
            "V2-VIEW-01",
            "Naim Elijah",
            "Admin view of every purchase across the platform. Calls SystemAdminService.viewGlobalHistory (UC-31). Also surfaces II.6.5 system analytics aggregations. Closes #40, #142, #143."
        );
        add(wireFilterBar("Date range", "Company", "Event", "Status"));
        add(wireGrid("Date", "Buyer", "Event", "Company", "Total", "Status"));
        add(wireCard("Aggregations",
            wireRow(
                wireBox("Total revenue"),
                wireBox("Order count"),
                wireBox("Top 5 events"),
                wireBox("Top 5 companies")
            )
        ));
    }
}
