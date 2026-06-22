package com.ticketing.system.Presentation.views.catalog;

import com.ticketing.system.Core.Application.dto.InventorySelectionDTO;
import com.ticketing.system.Core.Application.services.ReservationService;
import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBanner;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkCol;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkIconBtn;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.components.kit.LkStepper;
import com.ticketing.system.Presentation.components.venue.VkSeat;
import com.ticketing.system.Presentation.components.venue.VkSeatedZonePicker;
import com.ticketing.system.Presentation.components.venue.VkSeatLegend;
import com.ticketing.system.Presentation.components.venue.VkStandingZone;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.session.AuthSession;
import com.ticketing.system.Presentation.views.order.CartView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Anchor;
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
import java.util.UUID;

/**
 * Seat picker page (V2-RES-01). Hosts {@link VkSeatedZonePicker} for
 * seat-by-seat selection, a live selection rail, and a standing-zone section.
 */
@Route(value = "events/:eventId/seats/:zoneId", layout = MainLayout.class)
@PageTitle("Pick seats · TicketHub")
@AnonymousAllowed
public class SeatPickerView extends LkPage implements BeforeEnterObserver {

    /** Stub grid used until real seat data is loaded from a service. */
    private static final String[] LOWER_L_INITIAL = {
        "xxx..hh...",
        "xx...hh...",
        "x..mm.....",
        "....hh....",
        "..........",
        "...hh....."
    };

    private static final int SEAT_PRICE_CENTS = 16_000;
    private static final int SEAT_STEP        = 32;  // tile(28px) + gap(4px)
    private static final int ROW_LABEL_W      = 36;  // column reserved for the row letter

    private final ReservationService  reservationService;
    private final VkSeatedZonePicker  picker;

    private int eventId;
    private int zoneId;

    private LkCol selectionListCol;

    public SeatPickerView(ReservationService reservationService) {
        this.reservationService = reservationService;
        // TODO: replace buildSeatModels() with a real service call once
        //       CatalogService exposes getSeatedZoneSeats(eventId, zoneId).
        picker = new VkSeatedZonePicker(buildSeatModels(), this::updateSelectionRail);
        add(buildBreadcrumb());
        add(buildSeatedSplit());
        add(Lk.h2("Standing zone — quantity selection"));
        add(buildStandingSplit());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String eidStr = event.getRouteParameters().get("eventId").orElse("0");
        String zidStr = event.getRouteParameters().get("zoneId").orElse("0");
        try { this.eventId = Integer.parseInt(eidStr); } catch (NumberFormatException e) { this.eventId = 0; }
        try { this.zoneId  = Integer.parseInt(zidStr); } catch (NumberFormatException e) { this.zoneId  = 0; }
    }

    // ---------- stub data ----------

    /**
     * Converts the hardcoded char grid into {@link VkSeatedZonePicker.SeatModel} objects
     * with absolute (x, y) positions.
     *
     * TODO: swap for a real service call once CatalogService exposes seat data.
     */
    private static List<VkSeatedZonePicker.SeatModel> buildSeatModels() {
        List<VkSeatedZonePicker.SeatModel> seats = new ArrayList<>();
        for (int ri = 0; ri < LOWER_L_INITIAL.length; ri++) {
            char rowChar = (char) ('A' + ri);
            for (int ci = 0; ci < LOWER_L_INITIAL[ri].length(); ci++) {
                char c = LOWER_L_INITIAL[ri].charAt(ci);
                String label = "Row " + rowChar + " Seat " + (ci + 1);
                double x = ROW_LABEL_W + ci * (double) SEAT_STEP;
                double y = ri * (double) SEAT_STEP;
                VkSeat.State state = switch (c) {
                    case 'h' -> VkSeat.State.held;
                    case 'x' -> VkSeat.State.sold;
                    default  -> VkSeat.State.free; // '.' and 'm' start as free
                };
                seats.add(new VkSeatedZonePicker.SeatModel(label, x, y, state));
            }
        }
        return seats;
    }

    // ---------- breadcrumb ----------

    private Component buildBreadcrumb() {
        Div crumb = new Div();
        crumb.addClassName("vz-crumb");

        Anchor back = new Anchor("javascript:void(0)", "← Coldplay · pick area");
        back.addClassName("bz-link");
        back.getElement().addEventListener("click", e ->
            UI.getCurrent().navigate("events/coldplay"));

        Span trail = Lk.muted("/ Lower L · seated · $160");
        trail.getStyle().set("font-size", "13px");

        crumb.add(back, trail);
        return crumb;
    }

    // ---------- seated split: canvas + rail ----------

    private Component buildSeatedSplit() {
        Div split = new Div();
        split.addClassName("vz-split");
        split.add(buildSeatCanvas(), buildSelectionRail());
        return split;
    }

    private Component buildSeatCanvas() {
        LkCard card = new LkCard().pad(0);

        Div header = new Div();
        header.addClassName("vz-canvas-h");
        Div title = new Div();
        Span boldName = new Span();
        boldName.getElement().setProperty("innerHTML", "<b>Lower L</b>");
        Span sub = Lk.muted(" · Row A–F · click to select");
        sub.getStyle().set("font-size", "13px");
        title.add(boldName, sub);

        LkRow zoom = new LkRow().gap(6);
        zoom.add(new LkIconBtn(new LkIcon("minus", 16), "Zoom out"));
        zoom.add(new LkIconBtn(new LkIcon("crosshair", 16), "Fit"));
        zoom.add(new LkIconBtn(new LkIcon("plus", 16), "Zoom in"));

        header.add(title, zoom);
        card.add(header);

        Div canvas = new Div();
        canvas.addClassName("vz-canvas");

        Div stageStrip = new Div();
        stageStrip.addClassName("vz-stage-strip");
        stageStrip.setText("◄ FACING STAGE ►");
        canvas.add(stageStrip);

        Div seatScroll = new Div();
        seatScroll.addClassName("vz-seatscroll");

        // Row labels (A–F) share a relative wrapper with the picker so their
        // y-coordinates line up with the picker's absolute seat positions.
        Div pickerWrap = new Div();
        pickerWrap.getStyle().set("position", "relative");
        for (int ri = 0; ri < LOWER_L_INITIAL.length; ri++) {
            Span rowLabel = new Span(String.valueOf((char) ('A' + ri)));
            rowLabel.addClassName("vk-rowlabel");
            rowLabel.getStyle()
                    .set("position", "absolute")
                    .set("left", "0")
                    .set("top", (ri * SEAT_STEP) + "px");
            pickerWrap.add(rowLabel);
        }
        pickerWrap.add(picker);
        seatScroll.add(pickerWrap);
        canvas.add(seatScroll);

        Div legendWrap = new Div();
        legendWrap.getStyle().set("padding", "0 18px 16px");
        legendWrap.add(new VkSeatLegend());
        canvas.add(legendWrap);

        card.add(canvas);
        return card;
    }

    // ---------- selection rail ----------

    private Component buildSelectionRail() {
        LkCol rail = new LkCol().gap(14);
        rail.add(buildCountdownBanner());

        LkCard selection = new LkCard("Your selection").pad(16);
        selectionListCol = new LkCol().gap(10);
        selection.add(selectionListCol);
        rail.add(selection);

        rail.add(buildLockingExplainerCard());
        updateSelectionRail();
        return rail;
    }

    private Component buildCountdownBanner() {
        LkBanner banner = new LkBanner();
        banner.tone(LkBanner.Tone.warn);
        banner.setIcon(new LkIcon("clock", 17));

        Span timer = new Span("09:36");
        timer.addClassName("lk-mono");
        timer.getStyle().set("font-weight", "700");

        Span body = new Span();
        body.add(timer);
        Span msg = new Span(" — your seats are locked while you decide");
        msg.getStyle().set("font-size", "13px");
        body.add(msg);
        banner.setBody(body);
        return banner;
    }

    private void updateSelectionRail() {
        if (selectionListCol == null) return;
        selectionListCol.removeAll();

        if (!picker.hasSelection()) {
            Span hint = Lk.muted("Click a green seat above to select.");
            hint.getStyle().set("text-align", "center").set("display", "block").set("padding", "8px 0");
            selectionListCol.add(hint);
            return;
        }

        List<String> labels = picker.getSelection().getSeatNumbers();
        for (String label : labels) {
            selectionListCol.add(selectedSeatRow(label));
        }

        selectionListCol.add(Lk.divider());

        int count = labels.size();
        LkRow totalRow = new LkRow().justify("space-between");
        totalRow.add(Lk.muted(count + " seat" + (count == 1 ? "" : "s")));
        Span totalDisplay = new Span();
        totalDisplay.getElement().setProperty("innerHTML",
            "<b style='font-size:16px'>" + formatPrice((long) count * SEAT_PRICE_CENTS) + "</b>");
        totalRow.add(totalDisplay);
        selectionListCol.add(totalRow);

        LkBtn addBtn = new LkBtn("Add to cart →")
            .variant(LkBtn.Variant.primary)
            .size(LkBtn.Size.l)
            .full()
            .onClick(e -> addSelectionToCart());
        selectionListCol.add(addBtn);
    }

    private Component selectedSeatRow(String label) {
        Div row = new Div();
        row.addClassName("vz-selseat");

        Div info = new Div();
        Span name = new Span();
        name.getElement().setProperty("innerHTML", "<b>" + label + "</b>");
        Span subline = Lk.muted("Lower L · " + formatPrice(SEAT_PRICE_CENTS));
        subline.getStyle().set("font-size", "12.5px").set("display", "block");
        info.add(name, subline);

        Span remove = new Span();
        remove.addClassName("vz-selseat-x");
        remove.add(new LkIcon("close", 15));
        // picker.deselect() fires onSelectionChange → updateSelectionRail()
        remove.getElement().addEventListener("click", e -> picker.deselect(label));

        row.add(info, remove);
        return row;
    }

    private void addSelectionToCart() {
        InventorySelectionDTO selection = picker.getSelection();
        if (selection == null) return;
        int count = selection.getQuantity();
        try {
            callReserveService(selection);
            picker.clearSelection();
            Toasts.success(count + " seat" + (count == 1 ? "" : "s") + " reserved — continue to cart.");
            UI.getCurrent().navigate(CartView.class);
        } catch (Exception ex) {
            Toasts.failure("Could not reserve seats: " + ex.getMessage());
        }
    }

    // ---------- "How locking works" card ----------

    private Component buildLockingExplainerCard() {
        LkCard card = new LkCard("How locking works").pad(16);
        LkCol col = new LkCol().gap(9);
        col.add(lockLegendRow("mine", "<b>In your order</b> — held for you for 10 minutes."));
        col.add(lockLegendRow("held", "<b>Locked by others</b> — another buyer is mid-purchase. Updates live."));
        col.add(lockLegendRow("sold", "<b>Sold</b> — already purchased."));
        Span finePrint = Lk.muted("Two buyers can never hold the same seat — first tap wins (optimistic lock).");
        finePrint.getStyle().set("font-size", "12px").set("margin-top", "2px").set("display", "block");
        col.add(finePrint);
        card.add(col);
        return card;
    }

    private Component lockLegendRow(String stateClass, String html) {
        LkRow row = new LkRow().gap(10).align("flex-start");
        Span swatch = new Span();
        swatch.addClassName("vk-seat");
        swatch.addClassName("vk-seat-" + stateClass);
        swatch.addClassName("vk-legend-swatch");
        Span text = new Span();
        text.getElement().setProperty("innerHTML", html);
        text.getStyle().set("font-size", "13px");
        row.add(swatch, text);
        return row;
    }

    // ---------- standing zone + states reference ----------

    private Component buildStandingSplit() {
        Div split = new Div();
        split.addClassName("vz-split2");
        split.add(buildStandingCard(), buildStatesReferenceCard());
        return split;
    }

    private Component buildStandingCard() {
        LkCard card = new LkCard("General Admission · standing").pad(18);
        LkCol col = new LkCol().gap(16);
        col.add(new VkStandingZone("GA Floor", 7600, 9000, "$90 each"));

        LkRow qtyRow = new LkRow().gap(14).align("flex-end");
        LkStepper stepper = new LkStepper("2").label("Quantity").width("160px");
        Span policy = Lk.muted("Max 4 per buyer (policy)");
        policy.getStyle().set("font-size", "13px");
        qtyRow.add(stepper, policy);
        col.add(qtyRow);

        col.add(Lk.divider());

        LkRow priceRow = new LkRow().justify("space-between");
        priceRow.add(Lk.muted("2 × $90"));
        Span priceTotal = new Span();
        priceTotal.getElement().setProperty("innerHTML", "<b style='font-size:16px'>$180.00</b>");
        priceRow.add(priceTotal);
        col.add(priceRow);

        LkBtn hold = new LkBtn("Hold 2 tickets →")
            .variant(LkBtn.Variant.primary)
            .full()
            .onClick(e -> {
                try {
                    callReserveService(InventorySelectionDTO.standing(2));
                    Toasts.success("2 GA tickets reserved — continue to cart.");
                    UI.getCurrent().navigate(CartView.class);
                } catch (Exception ex) {
                    Toasts.failure("Could not reserve tickets: " + ex.getMessage());
                }
            });
        col.add(hold);

        card.add(col);
        return card;
    }

    private Component buildStatesReferenceCard() {
        LkCard card = new LkCard("Race-condition states")
            .subtitle("What the seat colours mean under load")
            .pad(18);

        Div grid = new Div();
        grid.addClassName("vz-state-grid");
        grid.add(stateCell(VkSeat.State.free, "",   "Available",        "Tap to hold"));
        grid.add(stateCell(VkSeat.State.mine, "14", "In your order",    "Locked 10 min"));
        grid.add(stateCell(VkSeat.State.held, "14", "Locked by others", "Live update"));
        grid.add(stateCell(VkSeat.State.sold, "14", "Sold",             "Unavailable"));
        card.add(grid);

        LkBanner info = new LkBanner();
        info.tone(LkBanner.Tone.info);
        info.setIcon(new LkIcon("info", 18));
        info.setBody(new Span(
            "On a sold-out event the system flips to a virtual queue; buyers get a waiting-room position before reaching this screen."));
        card.add(info);

        return card;
    }

    private Div stateCell(VkSeat.State state, String num, String title, String sub) {
        Div cell = new Div();
        cell.addClassName("vz-state-cell");
        VkSeat seat = new VkSeat(state, num.isEmpty() ? null : num);
        Div meta = new Div();
        Span t = new Span();
        t.getElement().setProperty("innerHTML", "<b style='font-size:13.5px'>" + title + "</b>");
        Span s = Lk.muted(sub);
        s.getStyle().set("font-size", "12px").set("display", "block");
        meta.add(t, s);
        cell.add(seat, meta);
        return cell;
    }

    // ---------- service calls ----------

    private void callReserveService(InventorySelectionDTO selection) {
        String token = AuthSession.token();
        if (token != null) {
            reservationService.reserveForMember(token, eventId, zoneId, selection);
        } else {
            reservationService.reserveForGuest(resolveGuestSessionId(), eventId, zoneId, selection);
        }
    }

    private String resolveGuestSessionId() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null) return UUID.randomUUID().toString();
        String guestId = (String) session.getAttribute("guestSessionId");
        if (guestId == null) {
            guestId = UUID.randomUUID().toString();
            session.setAttribute("guestSessionId", guestId);
        }
        return guestId;
    }

    private static String formatPrice(long cents) {
        return "$" + (cents / 100) + "." + String.format("%02d", cents % 100);
    }
}
