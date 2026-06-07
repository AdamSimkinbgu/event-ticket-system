package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "owner/venue/:eventId", layout = MainLayout.class)
@PageTitle("Venue map editor · Event Ticket Platform")
@PermitAll
public class VenueMapEditorView extends PlaceholderView {
    public VenueMapEditorView() {
        super(
            "Venue map editor",
            "V2-CADMIN-01",
            "Abed Faour",
            "Owner-side authoring of venue map + zones + seats for an event. Reuses VenueMapComponent (V2-CAT-03) in edit mode. Closes UC-19, UC-20, II.4.1.x, II.4.2.x."
        );
        add(wireSplit(1, 3,
            wireCard("Zones",
                wireGrid("Name", "Type", "Capacity"),
                wireActions("+ Add StandingZone", "+ Add SeatedZone")
            ),
            wireCard("Edit selected zone",
                wireForm("Name", "Price", "Type (StandingZone / SeatedZone)"),
                wireBox("If StandingZone → capacity NumberField"),
                wireBox("If SeatedZone → grid editor: rows × cols, each cell = Seat (label + (x,y))"),
                wireActions("Save zone", "Cancel")
            )
        ));
        add(wireBox("Whole-map save → EventManagementService.updateVenueMap"));
    }
}
