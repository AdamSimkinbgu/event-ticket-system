package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Core.Application.dto.VenueMapConfigDTO;
import com.ticketing.system.Core.Application.dto.ZoneDetailDTO;
import com.ticketing.system.Core.Application.services.EventManagementService;
import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBadge;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkCol;
import com.ticketing.system.Presentation.components.kit.LkGrid;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRadio;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.components.kit.LkStepper;
import com.ticketing.system.Presentation.components.venue.VkSeatBlock;
import com.ticketing.system.Presentation.components.venue.VkVenueMap;
import com.ticketing.system.Presentation.layouts.WorkspaceLayout;
import com.ticketing.system.Presentation.security.Capability;
import com.ticketing.system.Presentation.security.MockAuth;
import com.ticketing.system.Presentation.security.RequireCapability;
import com.ticketing.system.Presentation.session.MockSession;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Route(value = "owner/venue/:eventId", layout = WorkspaceLayout.class)
@PageTitle("Venue map · TicketHub")
@PermitAll
@RequireCapability(Capability.MANAGE_VENUE_MAPS)
public class VenueMapEditorView extends LkPage implements BeforeEnterObserver {

    private final EventManagementService eventService;
    private String eventId = "0";

    // In-memory zone list — editable before save
    private record ZoneState(String name, boolean seated, int rows, int seatsPerRow, int capacity, double price) {}
    private final List<ZoneState> zoneStates = new ArrayList<>(List.of(
        new ZoneState("VIP Floor",  true,  8,  40,    0, 250.0),
        new ZoneState("Lower L",    true,  15, 120,   0, 160.0),
        new ZoneState("GA Floor",   false, 0,  0,  9000,  90.0),
        new ZoneState("Lower R",    true,  15, 120,   0, 160.0),
        new ZoneState("Upper Tier", true,  20, 320,   0,  70.0)
    ));
    private int selectedZoneIdx = 0;

     private static final List<VkVenueMap.Zone> ZONES = List.of(
        new VkVenueMap.Zone("vip", "VIP Floor",         "$250", "sel",    null, "8%",  "26%", "48%", "20%"),
        new VkVenueMap.Zone("ll",  "Lower L",           "$160", "ok",     null, "32%", "6%",  "24%", "30%"),
        new VkVenueMap.Zone("ga",  "GA",                "$90",  "ok",     null, "32%", "32%", "36%", "30%"),
        new VkVenueMap.Zone("lr",  "Lower R",           "$160", "ok",     null, "32%", "70%", "24%", "30%"),
        new VkVenueMap.Zone("up",  "Upper",             "$70",  "ok",     null, "66%", "12%", "76%", "22%")
    );

    // Maps ZONES visual IDs → zoneStates list index
    private static final Map<String, Integer> ZONE_ID_TO_IDX = Map.of(
        "vip", 0,
        "ll",  1,
        "ga",  2,
        "lr",  3,
        "up",  4
    );

    // Wrapper div so we can swap the VkVenueMap when selection changes
    private final Div mapHolder = new Div();

    // Track selected visual zone id for the map highlight
    private String selectedZoneId = "vip";


    // Zone editor — Vaadin form fields (LkStepper/LkRadio are visual-only, no getValue)
    private final IntegerField           rowsField        = new IntegerField("Rows");
    private final IntegerField           seatsPerRowField = new IntegerField("Seats / row");
    private final RadioButtonGroup<String> zoneTypeGroup  = new RadioButtonGroup<>();
    private final TextField              zoneNameField    = new TextField("Zone name");
    private final TextField              priceField       = new TextField("Price");
    
    
    
    public VenueMapEditorView(EventManagementService eventService) {
        this.eventService = eventService;
        title("Venue map editor");
        subtitle("Coldplay · MOTS  ·  drag zones, then define seats");
        actions(
            new LkBtn("Cancel").variant(LkBtn.Variant.tertiary)
                .onClick(e -> UI.getCurrent().navigate(CompanyEventListView.class)),
            new LkBtn("Save map").variant(LkBtn.Variant.primary)
                .onClick(e -> saveMap())
        );
        add(buildSplit());
        loadZoneIntoEditor(0);  // pre-populate editor with zone 0
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        event.getRouteParameters().get("eventId").ifPresent(id -> this.eventId = id);
        loadZonesFromBackend();
    }

    private void loadZonesFromBackend() {
        String token = MockAuth.token();
        if (token == null || "0".equals(eventId)) return;
        try {
            List<ZoneDetailDTO> loaded = eventService.getEventZones(
                token, Integer.parseInt(eventId));
            if (!loaded.isEmpty()) {
                zoneStates.clear();
                for (ZoneDetailDTO z : loaded) {
                    zoneStates.add(new ZoneState(
                        z.name(), z.seated(), z.rows(), z.seatsPerRow(), z.capacity(), z.price()));
                }
                selectedZoneIdx = 0;
                loadZoneIntoEditor(0);
            }
            // If empty (no map configured yet), keep the placeholder defaults
        } catch (Exception ignored) {
            // Keep placeholder defaults if backend read fails
        }
    }

    private Component buildSplit() {
        Div split = new Div();
        split.addClassName("ow-venue-split");
        split.add(buildLeftCol(), buildZoneEditorCard());
        return split;
    }

    private Component buildLeftCol() {
        LkCol col = new LkCol().gap(14);
        col.add(buildZonesCard(), buildMapCard());
        return col;
    }

    private Component buildZonesCard() {
        LkCard card = new LkCard("Zones").pad(0);
        LkGrid grid = new LkGrid().dense()
            .col("Name",     "name")
            .col("Type",     "type")
            .col("Capacity", "cap", LkGrid.Align.RIGHT);
        zoneRow(grid, "VIP Floor", "Seated",   "320");
        zoneRow(grid, "Lower L",   "Seated",   "1,800");
        zoneRow(grid, "GA Floor",  "Standing", "9,000");
        zoneRow(grid, "Lower R",   "Seated",   "1,800");
        zoneRow(grid, "Upper Tier","Seated",   "6,400");
        grid.build();
        card.add(grid);

        Div actions = new Div();
        actions.addClassName("ow-card-actions");
        actions.add(
            new LkBtn("+ Standing").variant(LkBtn.Variant.secondary).size(LkBtn.Size.s)
                .onClick(e -> Toasts.success("New standing zone added (mock).")),
            new LkBtn("+ Seated").variant(LkBtn.Variant.secondary).size(LkBtn.Size.s)
                .onClick(e -> Toasts.success("New seated zone added (mock)."))
        );
        card.add(actions);
        return card;
    }

    private void zoneRow(LkGrid grid, String name, String type, String cap) {
        Map<String, Object> row = new LinkedHashMap<>();
        Span nameSpan = new Span();
        nameSpan.getElement().setProperty("innerHTML", "<b>" + name + "</b>");
        row.put("name", nameSpan);
        row.put("type", new LkBadge(type,
            "Standing".equals(type) ? LkBadge.Tone.contrast : LkBadge.Tone.primary).small());
        row.put("cap", cap);
        grid.row(row);
    }

   private Component buildMapCard() {
        LkCard card = new LkCard("Map preview").pad(14);
        refreshMapHolder();
        card.add(mapHolder);
        Span hint = Lk.muted("Click a zone to edit it  ·  selected: " + ZONES.get(selectedZoneIdx).label());
        hint.getStyle().set("font-size", "12px").set("margin-top", "8px").set("display", "block");
        card.add(hint);
        return card;
    }

    private void selectZone(String zoneId) {
        Integer idx = ZONE_ID_TO_IDX.get(zoneId);
        if (idx == null || idx < 0 || idx >= zoneStates.size()) return;
        selectedZoneIdx = idx;
        selectedZoneId = zoneId;
        loadZoneIntoEditor(idx);
        refreshMapHolder();   // re-render map with new highlighted zone
    }

    private void refreshMapHolder() {
        mapHolder.removeAll();
        mapHolder.add(new VkVenueMap(ZONES, selectedZoneId, true, this::selectZone));
    }

    private Component buildZoneEditorCard() {
       
        LkCard card = new LkCard("Edit zone · " + zoneStates.get(selectedZoneIdx).name()).pad(20);

        zoneNameField.setWidthFull();
        priceField.setPrefixComponent(new Span("$"));
        priceField.setWidthFull();

        Div formGrid = new Div();
        formGrid.getStyle()
            .set("display", "grid")
            .set("grid-template-columns", "1fr 1fr")
            .set("gap", "14px");
        formGrid.add(zoneNameField, priceField);

        zoneTypeGroup.setLabel("Zone type");
        zoneTypeGroup.setItems("Seated", "Standing");
        zoneTypeGroup.getStyle().set("margin-bottom", "8px");

        Div seatedEditor = new Div();
        seatedEditor.addClassName("ow-seated-editor");

        LkRow stepperRow = new LkRow().gap(12).align("flex-end");
        stepperRow.getStyle().set("margin-bottom", "12px");
        rowsField.setMin(1); rowsField.setMax(99); rowsField.setWidth("120px");
        seatsPerRowField.setMin(1); seatsPerRowField.setMax(500); seatsPerRowField.setWidth("120px");
        stepperRow.add(rowsField, seatsPerRowField);

        Div preview = new Div();
        preview.addClassName("ow-seat-preview");
        preview.add(new VkSeatBlock(new String[]{
            "..........", "..........", "..........", ".........."
        }));

        Span hint = Lk.muted("Each cell = one Seat (label + x,y). Tickets are pre-generated on save.");
        hint.getStyle().set("font-size", "12px").set("margin-top", "8px").set("display", "block");

        seatedEditor.add(stepperRow, preview, hint);

        // Show/hide seated editor based on zone type selection
        zoneTypeGroup.addValueChangeListener(ev ->
            seatedEditor.setVisible("Seated".equals(ev.getValue())));

        LkRow zoneActions = new LkRow().gap(8).justify("flex-end");
        zoneActions.getStyle().set("margin-top", "14px");
        zoneActions.add(
            new LkBtn("Cancel").variant(LkBtn.Variant.secondary)
                .onClick(e -> loadZoneIntoEditor(selectedZoneIdx)),
            new LkBtn("Save zone").variant(LkBtn.Variant.primary)
                .onClick(e -> saveZone())
        );

        LkCol col = new LkCol().gap(14);
        col.add(formGrid, zoneTypeGroup, seatedEditor, zoneActions);
        card.add(col);
        return card;
    }

        private void loadZoneIntoEditor(int idx) {
        ZoneState z = zoneStates.get(idx);
        zoneNameField.setValue(z.name());
        priceField.setValue(String.valueOf((int) z.price()));
        zoneTypeGroup.setValue(z.seated() ? "Seated" : "Standing");
        rowsField.setValue(z.rows());
        seatsPerRowField.setValue(z.seatsPerRow());
    }

    private void saveZone() {
        String name = zoneNameField.getValue().trim();
        if (name.isEmpty()) { Toasts.failure("Zone name is required."); return; }
        boolean seated = "Seated".equals(zoneTypeGroup.getValue());
        double price;
        try { price = Double.parseDouble(priceField.getValue().trim()); }
        catch (NumberFormatException ex) { Toasts.failure("Invalid price."); return; }

        ZoneState updated = seated
            ? new ZoneState(name, true,
                rowsField.getValue() == null ? 1 : rowsField.getValue(),
                seatsPerRowField.getValue() == null ? 1 : seatsPerRowField.getValue(),
                0, price)
            : new ZoneState(name, false, 0, 0,
                (rowsField.getValue() == null ? 0 : rowsField.getValue()),  // standing: reuse rows field as capacity
                price);

        zoneStates.set(selectedZoneIdx, updated);
        Toasts.success("Zone \"" + name + "\" updated — click Save map to persist.");
    }

    private void saveMap() {
        String token = MockAuth.token();
        if (token == null) { Toasts.failure("Session token missing — please log in again."); return; }
        String companyIdStr = MockSession.currentCompanyId();
        if (companyIdStr == null) { Toasts.failure("No company selected."); return; }

        List<VenueMapConfigDTO.ZoneConfigDTO> zoneDTOs = new ArrayList<>();
        for (ZoneState z : zoneStates) {
            if (z.seated()) {
                List<VenueMapConfigDTO.SeatConfigDTO> seats = generateSeats(z.rows(), z.seatsPerRow());
                zoneDTOs.add(new VenueMapConfigDTO.ZoneConfigDTO(
                    z.name(), true, null, seats, z.price()));
            } else {
                zoneDTOs.add(new VenueMapConfigDTO.ZoneConfigDTO(
                    z.name(), false, z.capacity(), null, z.price()));
            }
        }

        try {
            eventService.configureVenueMap(
                token,
                Integer.parseInt(companyIdStr),
                new VenueMapConfigDTO(eventId, "Venue", zoneDTOs)
            );
            Toasts.success("Venue map saved — tickets pre-generated.");
            UI.getCurrent().navigate(CompanyEventListView.class);
        } catch (Exception ex) {
            Toasts.failure("Could not save venue map: " + ex.getMessage());
        }
    }

    // Generates A1…H40 style labels with (col, row) coordinates.
    private List<VenueMapConfigDTO.SeatConfigDTO> generateSeats(int rows, int seatsPerRow) {
        List<VenueMapConfigDTO.SeatConfigDTO> seats = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            char rowLabel = (char) ('A' + r);
            for (int c = 1; c <= seatsPerRow; c++) {
                seats.add(new VenueMapConfigDTO.SeatConfigDTO(rowLabel + "" + c, c, r));
            }
        }
        return seats;
    }
}
