package com.ticketing.system.Presentation.views.catalog;

import com.ticketing.system.Core.Application.dto.GridPlacementDTO;
import com.ticketing.system.Core.Application.dto.InventoryZoneDTO;
import com.ticketing.system.Core.Application.dto.VenueMapDTO;
import com.ticketing.system.Core.Application.services.CatalogService;
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
import com.ticketing.system.Presentation.session.AuthSession;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Route(value = "events/:eventId", layout = MainLayout.class)
@PageTitle("Event · TicketHub")
@AnonymousAllowed
public class EventDetailsView extends LkPage implements BeforeEnterObserver {

    private final CatalogService catalogService;

    private int eventId;
    private List<InventoryZoneDTO> zones = List.of();
    private int gridRows = 3;
    private int gridCols = 3;
    private Integer selectedZoneId = null;

    private final Div mapContainer = new Div();
    private final Div zonesCardHolder = new Div();
    private Span selectedLine;

    public EventDetailsView(CatalogService catalogService) {
        this.catalogService = catalogService;
        add(buildHero());
        add(buildSplit());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        this.eventId = event.getRouteParameters().get("eventId")
                .map(s -> { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; } })
                .orElse(0);
        loadVenue();
    }

    private void loadVenue() {
        if (eventId <= 0) {
            return;
        }
        try {
            VenueMapDTO map = catalogService.getEventVenueMap(credential(), eventId);
            this.zones = map.inventoryZones();
            this.gridRows = map.gridRows();
            this.gridCols = map.gridCols();
            this.selectedZoneId = firstSelectableZoneId();
            refreshMap();
            refreshZonesCard();
            updateSelectedLine();
        } catch (RuntimeException ex) {
            // No (accessible) venue map — leave the placeholders; the rail shows a hint.
            this.zones = List.of();
            refreshMap();
            refreshZonesCard();
            updateSelectedLine();
        }
    }

    private String credential() {
        String token = AuthSession.token();
        if (token != null) {
            return token;
        }
        VaadinSession s = VaadinSession.getCurrent();
        Object g = s == null ? null : s.getAttribute("guestSessionId");
        return g == null ? "" : g.toString();
    }

    // -------- hero (event metadata — static placeholder; no public single-event read exists yet) --------

    private Component buildHero() {
        Div hero = new Div();
        hero.addClassName("bz-evt-hero");

        Div poster = new Div();
        poster.addClassName("bz-evt-hero-poster");
        poster.getStyle().set("background", "linear-gradient(135deg, #8b5cf6, #ec4899)");
        poster.setText("EVENT");

        Div meta = new Div();
        meta.addClassName("bz-evt-hero-meta");

        LkRow badges = new LkRow().gap(8);
        badges.add(new LkBadge("ON SALE", LkBadge.Tone.success).small());

        Div title = new Div();
        title.addClassName("bz-evt-title");
        title.setText("Event detail");

        Span sub = Lk.muted("Pick your area below to choose seats or quantity");
        sub.getStyle().set("font-size", "15px");

        meta.add(badges, title, sub);
        hero.add(poster, meta);
        return hero;
    }

    // -------- split: venue map + side rail --------

    private Component buildSplit() {
        Div split = new Div();
        split.addClassName("bz-evt-split");

        LkCard mapCard = new LkCard("Pick your area")
            .subtitle("Tap a zone to choose seats or quantity")
            .pad(16);

        mapContainer.add(renderMap());
        mapCard.add(mapContainer);

        Div legendWrap = new Div();
        legendWrap.getStyle().set("margin-top", "12px");
        legendWrap.add(new VkSeatLegend());
        mapCard.add(legendWrap);

        LkCol rail = new LkCol().gap(14);
        rail.add(buildZonesCard(), buildReserveCard());

        split.add(mapCard, rail);
        return split;
    }

    private VkVenueMap renderMap() {
        List<VkVenueMap.Zone> vk = new ArrayList<>();
        for (int i = 0; i < zones.size(); i++) {
            InventoryZoneDTO z = zones.get(i);
            GridPlacementDTO p = z.getPlacement() != null ? z.getPlacement() : defaultPlacement(i);
            vk.add(new VkVenueMap.Zone(
                String.valueOf(z.getId()),
                z.getName(),
                "$" + (int) z.getPrice(),
                toneFor(z),
                noteFor(z),
                pct((p.row() - 1) * 100.0 / gridRows),
                pct((p.col() - 1) * 100.0 / gridCols),
                pct(p.colSpan() * 100.0 / gridCols),
                pct(p.rowSpan() * 100.0 / gridRows)));
        }
        String sel = selectedZoneId == null ? null : String.valueOf(selectedZoneId);
        return new VkVenueMap(vk, sel, false, this::selectZone);
    }

    private static String toneFor(InventoryZoneDTO z) {
        if (z.getAvailableAmount() == 0) {
            return "danger";
        }
        return z.getAvailableAmount() <= Math.max(1, z.getCapacity() / 10) ? "warn" : "ok";
    }

    private static String noteFor(InventoryZoneDTO z) {
        if (z.getAvailableAmount() == 0) {
            return "Sold out";
        }
        return "STANDING".equals(z.getZoneType()) ? "Standing" : null;
    }

    private void selectZone(String zoneIdStr) {
        InventoryZoneDTO z = zoneById(zoneIdStr);
        if (z == null || z.getAvailableAmount() == 0) {
            return;
        }
        selectedZoneId = z.getId();
        refreshMap();
        updateSelectedLine();
    }

    private void refreshMap() {
        mapContainer.removeAll();
        mapContainer.add(renderMap());
    }

    private void updateSelectedLine() {
        if (selectedLine == null) {
            return;
        }
        InventoryZoneDTO z = selectedZoneId == null ? null : zoneById(String.valueOf(selectedZoneId));
        String label = z == null ? "—" : z.getName();
        selectedLine.getElement().setProperty("innerHTML",
            "<span style='color:var(--muted);font-size:13px'>Selected: <b style='color:var(--text)'>" + escape(label) + "</b></span>");
    }

    private Component buildZonesCard() {
        LkCard card = new LkCard("Zones").pad(14);
        card.add(zonesCardHolder);
        refreshZonesCard();
        return card;
    }

    private void refreshZonesCard() {
        zonesCardHolder.removeAll();
        if (zones.isEmpty()) {
            Span hint = Lk.muted("No zones available for this event yet.");
            hint.getStyle().set("font-size", "13px");
            zonesCardHolder.add(hint);
            return;
        }
        LkCol col = new LkCol().gap(6);
        for (InventoryZoneDTO z : zones) {
            boolean soldOut = z.getAvailableAmount() == 0;
            String type = "STANDING".equals(z.getZoneType()) ? "standing" : "seated";
            col.add(zoneRow(z.getName() + " · " + type + (soldOut ? " · sold out" : ""),
                    "$" + (int) z.getPrice(), soldOut));
        }
        zonesCardHolder.add(col);
    }

    private Div zoneRow(String label, String price, boolean muted) {
        Div row = new Div();
        row.addClassName("bz-zonerow");
        if (muted) {
            row.addClassName("muted");
        }
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
            .onClick(e -> openPicker());
        choose.getStyle().set("margin-top", "10px");
        Span hint = Lk.muted("Seats lock for 10 min once selected");
        hint.getStyle()
            .set("display", "block").set("margin-top", "8px")
            .set("text-align", "center").set("font-size", "12.5px");
        card.add(selectedLine, choose, hint);
        return card;
    }

    private void openPicker() {
        if (eventId > 0 && selectedZoneId != null) {
            UI.getCurrent().navigate("events/" + eventId + "/seats/" + selectedZoneId);
        }
    }

    // -------- helpers --------

    private InventoryZoneDTO zoneById(String idStr) {
        try {
            int id = Integer.parseInt(idStr);
            return zones.stream().filter(z -> z.getId() == id).findFirst().orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer firstSelectableZoneId() {
        return zones.stream().filter(z -> z.getAvailableAmount() > 0).findFirst()
                .map(InventoryZoneDTO::getId).orElse(null);
    }

    /** Auto-layout fallback for zones the owner never placed: flow into 1×1 cells by index. */
    private GridPlacementDTO defaultPlacement(int idx) {
        int row = Math.min((idx / Math.max(gridCols, 1)) + 1, gridRows);
        int col = (idx % Math.max(gridCols, 1)) + 1;
        return new GridPlacementDTO(row, col, 1, 1);
    }

    private static String pct(double v) {
        return String.format(Locale.US, "%.2f%%", v);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
