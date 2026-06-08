package com.ticketing.system.Presentation.views.company;

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
import com.ticketing.system.Presentation.layouts.AdminLayout;
import com.ticketing.system.Presentation.security.RequiresOwnerCompany;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Route(value = "owner/venue/:eventId", layout = AdminLayout.class)
@PageTitle("Venue map · TicketHub")
@PermitAll
public class VenueMapEditorView extends LkPage implements RequiresOwnerCompany {

    private static final List<VkVenueMap.Zone> ZONES = List.of(
        new VkVenueMap.Zone("vip", "VIP Floor",         "$250", "sel",    null, "8%",  "26%", "48%", "20%"),
        new VkVenueMap.Zone("ll",  "Lower L",           "$160", "ok",     null, "32%", "6%",  "24%", "30%"),
        new VkVenueMap.Zone("ga",  "GA",                "$90",  "ok",     null, "32%", "32%", "36%", "30%"),
        new VkVenueMap.Zone("lr",  "Lower R",           "$160", "ok",     null, "32%", "70%", "24%", "30%"),
        new VkVenueMap.Zone("up",  "Upper",             "$70",  "ok",     null, "66%", "12%", "76%", "22%")
    );

    public VenueMapEditorView() {
        title("Venue map editor");
        subtitle("Coldplay · MOTS  ·  drag zones, then define seats");
        actions(
            new LkBtn("Cancel").variant(LkBtn.Variant.tertiary)
                .onClick(e -> UI.getCurrent().navigate(CompanyEventListView.class)),
            new LkBtn("Save map").variant(LkBtn.Variant.primary)
                .onClick(e -> {
                    Toasts.success("Venue map saved — tickets pre-generated.");
                    UI.getCurrent().navigate(CompanyEventListView.class);
                })
        );
        add(buildSplit());
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
        card.add(new VkVenueMap(ZONES, "vip", true, id -> { /* zone drag mock */ }));
        Span hint = Lk.muted("Drag a zone to reposition  ·  selected: VIP Floor");
        hint.getStyle().set("font-size", "12px").set("margin-top", "8px").set("display", "block");
        card.add(hint);
        return card;
    }

    private Component buildZoneEditorCard() {
        LkCard card = new LkCard("Edit zone · VIP Floor").pad(20);

        TextField zoneName = new TextField("Zone name");
        zoneName.setValue("VIP Floor");
        zoneName.setWidthFull();

        TextField price = new TextField("Price");
        price.setValue("250");
        price.setPrefixComponent(new Span("$"));
        price.setWidthFull();

        Div formGrid = new Div();
        formGrid.getStyle()
            .set("display", "grid")
            .set("grid-template-columns", "1fr 1fr")
            .set("gap", "14px");
        formGrid.add(zoneName, price);

        LkRadio zoneType = new LkRadio().label("Zone type").options("Seated", "Seated", "Standing");

        Div seatedEditor = new Div();
        seatedEditor.addClassName("ow-seated-editor");

        LkRow stepperRow = new LkRow().gap(12).align("flex-end");
        stepperRow.getStyle().set("margin-bottom", "12px");
        stepperRow.add(
            new LkStepper("8").label("Rows").width("120px"),
            new LkStepper("40").label("Seats / row").width("120px"),
            Lk.muted("= 320 seats · labels A1…H40")
        );

        Div preview = new Div();
        preview.addClassName("ow-seat-preview");
        preview.add(new VkSeatBlock(new String[]{
            "..........", "..........", "..........", ".........."
        }));

        Span hint = Lk.muted("Each cell = one Seat (label + x,y). Tickets are pre-generated on save.");
        hint.getStyle().set("font-size", "12px").set("margin-top", "8px").set("display", "block");

        seatedEditor.add(stepperRow, preview, hint);

        LkRow zoneActions = new LkRow().gap(8).justify("flex-end");
        zoneActions.getStyle().set("margin-top", "14px");
        zoneActions.add(
            new LkBtn("Cancel").variant(LkBtn.Variant.secondary)
                .onClick(e -> Toasts.warn("Zone edit cancelled.")),
            new LkBtn("Save zone").variant(LkBtn.Variant.primary)
                .onClick(e -> Toasts.success("Zone saved."))
        );

        LkCol col = new LkCol().gap(14);
        col.add(formGrid, zoneType, seatedEditor, zoneActions);
        card.add(col);
        return card;
    }
}
