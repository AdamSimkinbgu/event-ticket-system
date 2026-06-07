package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Presentation.layouts.AdminLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "owner/events", layout = AdminLayout.class)
@PageTitle("Events · Owner workspace")
@PermitAll
public class CompanyEventListView extends PlaceholderView {
    public CompanyEventListView() {
        super(
            "My events",
            "V2-CADMIN-06",
            "Abed Faour",
            "Owner-side list of every event under the selected company. Entry point to EventManagementView (edit metadata), VenueMapEditorView (zones + seats), and the cancel-event dialog. Backed by EventManagementService.findEventsByCompany."
        );
        add(wireFilterBar("Company (if multiple)", "Status (Upcoming · Live · Cancelled · Past)", "Date range"));
        add(wireGrid("Event", "Date", "Venue", "Tickets sold", "Status", "Actions"));
        add(wireBox("Per row: Edit metadata · Venue map · Policies · Sales · ⚠ Cancel event (opens EventCancelDialog)"));
        add(wireActions("+ New event", "Bulk export"));
    }
}
