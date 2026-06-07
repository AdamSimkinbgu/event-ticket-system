package com.ticketing.system.Presentation.views.catalog;

import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBadge;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkCol;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.components.venue.VkSeatLegend;
import com.ticketing.system.Presentation.components.venue.VkVenueMap;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.views.order.CartView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.List;

@Route(value = "events/:eventId", layout = MainLayout.class)
@PageTitle("Event · TicketHub")
@AnonymousAllowed
public class EventDetailsView extends LkPage {

    private static final List<VkVenueMap.Zone> COLDPLAY_ZONES = List.of(
        new VkVenueMap.Zone("vip",   "VIP Floor",         "$250", "warn",   "Few left",   "8%",  "26%", "48%", "20%"),
        new VkVenueMap.Zone("lwr-l", "Lower L",           "$160", "ok",     null,         "32%", "6%",  "24%", "30%"),
        new VkVenueMap.Zone("ga",    "General Admission", "$90",  "ok",     "Standing",   "32%", "32%", "36%", "30%"),
        new VkVenueMap.Zone("lwr-r", "Lower R",           "$160", "ok",     null,         "32%", "70%", "24%", "30%"),
        new VkVenueMap.Zone("upper", "Upper Tier",        "$70",  "danger", "Sold out",   "66%", "12%", "76%", "22%")
    );

    private String selectedZoneId = "ga";
    private Div mapContainer;
    private Span selectedLine;

    public EventDetailsView() {
        add(buildHero());
        add(buildSplit());
    }

    // -------- hero --------

    private Component buildHero() {
        Div hero = new Div();
        hero.addClassName("bz-evt-hero");

        Div poster = new Div();
        poster.addClassName("bz-evt-hero-poster");
        poster.getStyle().set("background", "linear-gradient(135deg, #8b5cf6, #ec4899)");
        poster.setText("CONCERT");

        Div meta = new Div();
        meta.addClassName("bz-evt-hero-meta");

        LkRow badges = new LkRow().gap(8);
        badges.add(new LkBadge("ON SALE", LkBadge.Tone.success).small());

        LkBadge rating = new LkBadge("", LkBadge.Tone.muted).small();
        rating.removeAll();
        Span ratingInner = new Span();
        ratingInner.getStyle().set("display", "inline-flex").set("align-items", "center").set("gap", "4px");
        ratingInner.add(new LkIcon("star", 12), new Span("4.8"));
        rating.add(ratingInner);
        badges.add(rating);

        Div title = new Div();
        title.addClassName("bz-evt-title");
        title.setText("Coldplay · Music of the Spheres");

        Span sub = Lk.muted("Park HaYarkon, Tel Aviv · by Live Nation Israel");
        sub.getStyle().set("font-size", "15px");

        LkRow info = new LkRow().gap(18);
        info.getStyle().set("margin-top", "4px");
        info.add(infoLine("calendar", "Thu 26 Jun 2026"));
        info.add(infoLine("clock", "Doors 19:00 · Show 20:00"));
        Span priceLine = new Span();
        priceLine.getElement().setProperty("innerHTML", "From <b>$70</b>");
        priceLine.getStyle().set("font-size", "14px");
        info.add(priceLine);

        meta.add(badges, title, sub, info);
        hero.add(poster, meta);
        return hero;
    }

    private Span infoLine(String icon, String text) {
        Span s = new Span();
        s.getStyle().set("font-size", "14px").set("display", "inline-flex").set("align-items", "center").set("gap", "4px");
        s.add(new LkIcon(icon, 15));
        s.add(new Span(text));
        return s;
    }

    // -------- split: venue map + side rail --------

    private Component buildSplit() {
        Div split = new Div();
        split.addClassName("bz-evt-split");

        LkCard mapCard = new LkCard("Pick your area")
            .subtitle("Tap a zone to choose seats or quantity")
            .pad(16);

        mapContainer = new Div();
        mapContainer.add(renderMap());
        mapCard.add(mapContainer);

        Div legendWrap = new Div();
        legendWrap.getStyle().set("margin-top", "12px");
        legendWrap.add(new VkSeatLegend());
        mapCard.add(legendWrap);

        LkCol rail = new LkCol().gap(14);
        rail.add(buildShowTimesCard(), buildZonesCard(), buildReserveCard());

        split.add(mapCard, rail);
        return split;
    }

    private VkVenueMap renderMap() {
        return new VkVenueMap(COLDPLAY_ZONES, selectedZoneId, false, this::selectZone);
    }

    private void selectZone(String zoneId) {
        VkVenueMap.Zone zone = zoneFor(zoneId);
        if (zone == null || "danger".equals(zone.tone())) return;
        selectedZoneId = zoneId;
        mapContainer.removeAll();
        mapContainer.add(renderMap());
        updateSelectedLine();
    }

    private VkVenueMap.Zone zoneFor(String id) {
        return COLDPLAY_ZONES.stream().filter(z -> z.id().equals(id)).findFirst().orElse(null);
    }

    private void updateSelectedLine() {
        if (selectedLine == null) return;
        VkVenueMap.Zone z = zoneFor(selectedZoneId);
        String label = z == null ? "—" : z.label();
        selectedLine.getElement().setProperty("innerHTML",
            "<span style='color:var(--muted);font-size:13px'>Selected: <b style='color:var(--text)'>" + escape(label) + "</b></span>");
    }

    private Component buildShowTimesCard() {
        LkCard card = new LkCard("Show times").pad(14);
        LkCol col = new LkCol().gap(8);
        col.add(showtime("Thu 26 Jun", "20:00 · selected", true));
        col.add(showtime("Fri 27 Jun", "20:00", false));
        card.add(col);
        return card;
    }

    private Div showtime(String day, String sub, boolean selected) {
        Div row = new Div();
        row.addClassName("bz-showtime");
        if (selected) row.addClassName("on");
        Div label = new Div();
        Span d = new Span();
        d.getElement().setProperty("innerHTML", "<b>" + day + "</b>");
        Div s = new Div();
        s.addClassName("bz-showtime-sub");
        s.setText(sub);
        label.add(d, s);
        Span dot = new Span();
        dot.addClassName("lk-radio-dot");
        if (selected) dot.addClassName("on");
        row.add(label, dot);
        return row;
    }

    private Component buildZonesCard() {
        LkCard card = new LkCard("Zones").pad(14);
        LkCol col = new LkCol().gap(6);
        col.add(zoneRow("VIP Floor · seated",           "$250", false));
        col.add(zoneRow("Lower L / R · seated",         "$160", false));
        col.add(zoneRow("General Admission · standing", "$90",  false));
        col.add(zoneRow("Upper Tier · sold out",        "$70",  true));
        card.add(col);
        return card;
    }

    private Div zoneRow(String label, String price, boolean muted) {
        Div row = new Div();
        row.addClassName("bz-zonerow");
        if (muted) row.addClassName("muted");
        Span lbl = new Span(label);
        Span pr = new Span();
        pr.getElement().setProperty("innerHTML", "<b>" + price + "</b>");
        row.add(lbl, pr);
        return row;
    }

    private Component buildReserveCard() {
        LkCard card = new LkCard().pad(16).accent();
        selectedLine = new Span();
        updateSelectedLine();

        LkBtn choose = new LkBtn("Choose tickets →")
            .variant(LkBtn.Variant.primary)
            .size(LkBtn.Size.l)
            .full()
            .onClick(e -> {
                VkVenueMap.Zone z = zoneFor(selectedZoneId);
                String label = z == null ? "selection" : z.label();
                Toasts.success(label + " held for 10 min. Continue to cart.");
                UI.getCurrent().navigate(CartView.class);
            });
        choose.getStyle().set("margin-top", "10px");
        Span hint = Lk.muted("Seats lock for 10 min once selected");
        hint.getStyle()
            .set("display", "block").set("margin-top", "8px")
            .set("text-align", "center").set("font-size", "12.5px");
        card.add(selectedLine, choose, hint);
        return card;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
