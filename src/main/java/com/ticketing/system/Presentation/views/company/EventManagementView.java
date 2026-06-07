package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Presentation.layouts.AdminLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "owner/events/:eventId", layout = AdminLayout.class)
@PageTitle("Edit event · Owner workspace")
@PermitAll
public class EventManagementView extends PlaceholderView {
    public EventManagementView() {
        super(
            "Edit event",
            "V2-CADMIN-04",
            "Abed Faour",
            "Owner-side editor for an event's basic metadata (UC-19). Sibling tabs / links open VenueMapEditorView (UC-20), PurchasePolicyEditorView (UC-21), and the cancel-event dialog. Calls EventManagementService.updateEvent."
        );
        add(wireSplit(2, 1,
            wireCard("Event details",
                wireForm(
                    "Title (required)",
                    "Category (Concert · Sport · Theatre · Conference)",
                    "Description (rich text)",
                    "Start date / time",
                    "End date / time",
                    "Location (Venue name + address)",
                    "Max attendance (NumberField)"
                )
            ),
            wireColumn(
                wireCard("Linked editors",
                    wireBox("🎟  Venue map + zones  →  V2-CADMIN-01"),
                    wireBox("📜  Purchase policies  →  V2-PEDIT-01"),
                    wireBox("📊  Sales for this event  →  V2-VIEW-02 (filtered)")
                ),
                wireCard("Status",
                    wireBox("Current: DRAFT · UPCOMING · LIVE · ENDED · CANCELLED"),
                    wireBox("Tickets sold: 0 / max")
                ),
                wireCard("Danger zone",
                    wireBox("⚠ Cancel this event — opens EventCancelDialog (refunds all holders)")
                )
            )
        ));
        add(wireActions("Save changes", "Discard"));
    }
}
