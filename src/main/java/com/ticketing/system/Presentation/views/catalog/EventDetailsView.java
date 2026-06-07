package com.ticketing.system.Presentation.views.catalog;

import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route(value = "events/:eventId", layout = MainLayout.class)
@PageTitle("Event · Event Ticket Platform")
@AnonymousAllowed
public class EventDetailsView extends PlaceholderView {
    public EventDetailsView() {
        super(
            "Event details",
            "V2-CAT-02",
            "Naim Elijah",
            "Event metadata + venue map + zones. Click a zone → opens SeatPicker (seated) or QuantitySelector (standing). Honours UC-8."
        );
        add(wireHero("Event name", "Concert · Category · ★ 4.8 · Venue · Production Company"));
        add(wireSplit(2, 1,
            wireCard("Venue map",
                wireBox("VenueMapComponent (V2-CAT-03) — stage + seated zones + standing capacity bar")
            ),
            wireColumn(
                wireCard("Show times",
                    wireBox("Date 1 · 20:00"),
                    wireBox("Date 2 · 20:00")
                ),
                wireCard("Zones",
                    wireBox("VIP (seated) · $200"),
                    wireBox("General Admission · $50")
                ),
                wireActions("Reserve")
            )
        ));
    }
}
