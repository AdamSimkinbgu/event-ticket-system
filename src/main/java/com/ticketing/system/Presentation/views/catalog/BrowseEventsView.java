package com.ticketing.system.Presentation.views.catalog;

import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.views.PlaceholderView;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Browse Events · Event Ticket Platform")
@AnonymousAllowed
public class BrowseEventsView extends PlaceholderView {

    public BrowseEventsView() {
        super(
            "Browse events",
            "V2-CAT-01",
            "Naim Elijah",
            "The homepage. Lists events with search + filters, calling CatalogService.search(...). Open to guests and members alike."
        );

        add(wireHeroSearch(
            "Find your next experience",
            "Concerts, matches, theatre, conferences — across Israel.",
            "Search events, artists, venues…"
        ));

        add(wireCategoryChips("All", "🎵 Concerts", "⚽ Sports", "🎭 Theatre", "🎤 Conferences"));

        add(wireSectionTitle("Featured this week"));
        add(wireCardGrid(
            wireEventPoster("Concert",    "Coldplay Live in Tel Aviv",   "Park HaYarkon  ·  26 Jun  ·  20:00",   "From $250"),
            wireEventPoster("Sport",      "Hapoel TLV vs Maccabi Haifa", "Bloomfield Stadium  ·  28 Jun  ·  21:00", "From $80"),
            wireEventPoster("Theatre",    "Othello at Habima",           "Habima National Theatre  ·  30 Jun",   "From $120"),
            wireEventPoster("Conference", "Spring AI Conference 2026",   "David Intercontinental  ·  12 Jul",    "From $400")
        ));

        add(wireSectionTitle("All events"));
        add(wireSplit(1, 3,
            wireFilterSidebar("Category", "Date range", "Price range"),
            wireDataGrid(
                new String[]{"Event", "Category", "Venue", "Date", "From", "Status"},
                new String[]{"Coldplay Live in Tel Aviv",     "Concert",    "Park HaYarkon",        "26 Jun",  "$250", "● On sale"},
                new String[]{"Hapoel TLV vs Maccabi Haifa",   "Sport",      "Bloomfield Stadium",   "28 Jun",  "$80",  "● On sale"},
                new String[]{"Othello at Habima",             "Theatre",    "Habima Theatre",       "30 Jun",  "$120", "● On sale"},
                new String[]{"Mashina 35-Year Tour",          "Concert",    "TLV Convention Center","5 Jul",   "$180", "▲ Selling fast"},
                new String[]{"Beitar Jerusalem vs Hapoel BS", "Sport",      "Teddy Stadium",        "7 Jul",   "$60",  "● On sale"},
                new String[]{"Spring AI Conference 2026",     "Conference", "David Intercontinental","12 Jul", "$400", "▲ Few left"},
                new String[]{"Eden Hason live",               "Concert",    "Caesarea Amphitheatre","20 Jul",  "$220", "● On sale"},
                new String[]{"Beitar Jerusalem vs Maccabi TA","Sport",      "Teddy Stadium",        "23 Jul",  "$75",  "○ Pre-sale"}
            )
        ));
    }
}
