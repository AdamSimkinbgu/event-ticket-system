package com.ticketing.system.Presentation.views.company;

import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBadge;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkCol;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.layouts.WorkspaceLayout;
import com.ticketing.system.Presentation.security.Capabilities;
import com.ticketing.system.Presentation.security.Capability;
import com.ticketing.system.Presentation.security.RequireCapability;
import com.ticketing.system.Presentation.views.admin.CompanySalesView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "owner/events/:eventId", layout = WorkspaceLayout.class)
@PageTitle("Edit event · TicketHub")
@PermitAll
@RequireCapability(Capability.EDIT_COMPANY_EVENTS)
public class EventManagementView extends LkPage {

    private final TextField title = new TextField("Title");
    private final TextField category = new TextField("Category");
    private final TextField start = new TextField("Start");
    private final TextField end = new TextField("End");
    private final TextField venue = new TextField("Venue");
    private final TextField city = new TextField("City / address");
    private final IntegerField maxAttend = new IntegerField("Max attendance");
    private final TextArea description = new TextArea("Description");

    public EventManagementView() {
        title("Edit event");
        subtitle("Coldplay · Music of the Spheres");
        actions(
                new LkBtn("Discard").variant(LkBtn.Variant.tertiary)
                        .onClick(e -> UI.getCurrent().navigate(CompanyEventListView.class)),
                new LkBtn("Save changes").variant(LkBtn.Variant.primary)
                        .onClick(e -> {
                            Toasts.success("Event saved.");
                            UI.getCurrent().navigate(CompanyEventListView.class);
                        }));
        add(buildSplit());
    }

    private Component buildSplit() {
        Div split = new Div();
        split.addClassName("ow-edit-split");
        split.add(buildDetailsCard(), buildSideCol());
        return split;
    }

    private Component buildDetailsCard() {
        LkCard card = new LkCard("Event details").pad(20).flush();

        title.setValue("Coldplay · Music of the Spheres");
        title.setRequired(true);
        title.setWidthFull();

        category.setValue("Concert");
        category.setWidthFull();

        start.setValue("Thu 26 Jun 2026 · 20:00");
        start.setSuffixComponent(new LkIcon("calendar", 16));
        start.setWidthFull();

        end.setValue("Thu 26 Jun 2026 · 23:30");
        end.setSuffixComponent(new LkIcon("calendar", 16));
        end.setWidthFull();

        venue.setValue("Park HaYarkon");
        venue.setWidthFull();

        city.setValue("Rokach Blvd, Tel Aviv");
        city.setWidthFull();

        maxAttend.setValue(45000);
        maxAttend.setStepButtonsVisible(true);
        maxAttend.setMin(0);
        maxAttend.setWidthFull();

        description.setValue("Coldplay return to Tel Aviv on their record-breaking Music of the Spheres world tour…");
        description.setMinHeight("120px");
        description.setWidthFull();

        Div grid = new Div();
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fit, minmax(min(100%, 220px), 1fr))")
                .set("gap", "14px");
        grid.add(title, category, start, end, venue, city, maxAttend);

        LkCol col = new LkCol().gap(14);
        col.add(grid, description);
        card.add(col);
        return card;
    }

    private Component buildSideCol() {
        LkCol col = new LkCol().gap(14);
        col.add(buildLinkedEditorsCard(), buildStatusCard());
        // Danger zone (cancel event) is owner-only — managers can edit details
        // but cannot cancel. Drop the entire card for users without the cap.
        if (Capabilities.has(Capability.CANCEL_EVENT)) {
            col.add(buildDangerCard());
        }
        return col;
    }

    private Component buildLinkedEditorsCard() {
        LkCard card = new LkCard("Linked editors").pad(14);
        LkCol col = new LkCol().gap(8);
        col.add(
                linkRow("ticket", "Venue map + zones", () -> UI.getCurrent().navigate("owner/venue/coldplay")),
                linkRow("policy", "Purchase policies", () -> UI.getCurrent().navigate(PurchasePolicyEditorView.class)),
                linkRow("chart", "Sales for this event", () -> UI.getCurrent().navigate(CompanySalesView.class)));
        card.add(col);
        return card;
    }

    private Div linkRow(String iconName, String label, Runnable r) {
        Div row = new Div();
        row.addClassName("ow-link-row");
        row.add(new LkIcon(iconName, 17));
        Span lbl = new Span(label);
        lbl.getStyle().set("flex", "1");
        Span arrow = new Span("→");
        row.add(lbl, arrow);
        row.getElement().addEventListener("click", e -> r.run());
        return row;
    }

    private Component buildStatusCard() {
        LkCard card = new LkCard("Status").pad(14);
        LkRow badges = new LkRow().gap(8);
        badges.getStyle().set("margin-bottom", "8px");
        badges.add(new LkBadge("LIVE", LkBadge.Tone.success).small());
        card.add(badges);

        Span lbl = Lk.muted("Tickets sold");
        lbl.getStyle().set("font-size", "13px").set("display", "block");
        card.add(lbl);

        Div range = new Div();
        range.addClassName("bz-range");
        range.getStyle().set("margin-top", "6px");
        Span track = new Span();
        track.addClassName("bz-range-track");
        Span fill = new Span();
        fill.addClassName("bz-range-fill");
        fill.getStyle().set("width", "85%").set("left", "0").set("right", "auto");
        track.add(fill);
        range.add(track);
        card.add(range);

        Span counter = new Span();
        counter.getElement().setProperty("innerHTML", "<b>38,420</b> / 45,000");
        counter.getStyle().set("font-size", "13px").set("display", "block").set("margin-top", "8px");
        card.add(counter);
        return card;
    }

    private Component buildDangerCard() {
        LkCard card = new LkCard("Danger zone").pad(14).danger();
        Span warn = Lk.muted("Cancelling refunds every ticket holder and notifies them.");
        warn.getStyle().set("font-size", "13px").set("display", "block").set("margin-bottom", "10px");
        card.add(warn);
        card.add(new LkBtn("Cancel this event").variant(LkBtn.Variant.error).full()
                .icon(new LkIcon("warning", 16))
                .onClick(e -> Toasts.warn("Cancel-event dialog refunds all holders + emails them.")));
        return card;
    }
}
