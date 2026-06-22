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
import com.ticketing.system.Presentation.components.venue.VkSeatLegend;
import com.ticketing.system.Presentation.components.venue.VkStandingZone;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.session.AuthSession;
import com.ticketing.system.Presentation.session.GuestSession;
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
 * Centerpiece seat picker (V2-CAT-03). Interactive seat grid with the
 * four race-condition states (free / mine / held / sold), live
 * selection rail with running total, plus a standing-zone variant and
 * a states reference card.
 */
@Route(value = "events/:eventId/seats/:zoneId", layout = MainLayout.class)
@PageTitle("Pick seats · TicketHub")
@AnonymousAllowed
public class SeatPickerView extends LkPage implements BeforeEnterObserver {

    /** {@code . = free, m = mine, h = held by another buyer, x = sold} */
    private static final String[] LOWER_L_INITIAL = {
        "xxx..hh...",
        "xx...hh...",
        "x..mm.....",
        "....hh....",
        "..........",
        "...hh....."
    };

    private static final int SEAT_PRICE_CENTS = 16000;

    private final ReservationService reservationService;

    private int eventId;
    private int zoneId;

    private char[][] seatStates;
    private Div seatGridContainer;
    private LkCol selectionListCol;

    public SeatPickerView(ReservationService reservationService) {
        this.reservationService = reservationService;
        initSeatStates();
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

    private void initSeatStates() {
        seatStates = new char[LOWER_L_INITIAL.length][];
        for (int i = 0; i < LOWER_L_INITIAL.length; i++) {
            seatStates[i] = LOWER_L_INITIAL[i].toCharArray();
        }
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
        seatGridContainer = new Div();
        renderSeatGrid();
        seatScroll.add(seatGridContainer);
        canvas.add(seatScroll);

        Div legendWrap = new Div();
        legendWrap.getStyle().set("padding", "0 18px 16px");
        legendWrap.add(new VkSeatLegend());
        canvas.add(legendWrap);

        card.add(canvas);
        return card;
    }

    private void renderSeatGrid() {
        seatGridContainer.removeAll();
        Div block = new Div();
        block.addClassName("vk-seatblock");

        for (int ri = 0; ri < seatStates.length; ri++) {
            Div row = new Div();
            row.addClassName("vk-seatrow");

            Span label = new Span(String.valueOf((char) ('A' + ri)));
            label.addClassName("vk-rowlabel");
            row.add(label);

            for (int ci = 0; ci < seatStates[ri].length; ci++) {
                char c = seatStates[ri][ci];
                VkSeat.State state = stateFor(c);
                VkSeat seat = new VkSeat(state, String.valueOf(ci + 1));
                if (state == VkSeat.State.free || state == VkSeat.State.mine) {
                    final int rr = ri, cc = ci;
                    seat.addClickListener(e -> handleSeatClick(rr, cc));
                }
                row.add(seat);
            }
            block.add(row);
        }
        seatGridContainer.add(block);
    }

    private static VkSeat.State stateFor(char c) {
        return switch (c) {
            case 'm' -> VkSeat.State.mine;
            case 'h' -> VkSeat.State.held;
            case 'x' -> VkSeat.State.sold;
            default  -> VkSeat.State.free;
        };
    }

    private void handleSeatClick(int row, int col) {
        char current = seatStates[row][col];
        if (current == 'h' || current == 'x') return;
        seatStates[row][col] = (current == 'm') ? '.' : 'm';
        renderSeatGrid();
        updateSelectionRail();
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

        List<int[]> mineSeats = collectMine();
        if (mineSeats.isEmpty()) {
            Span hint = Lk.muted("Click a green seat above to select.");
            hint.getStyle().set("text-align", "center").set("display", "block").set("padding", "8px 0");
            selectionListCol.add(hint);
            return;
        }

        for (int[] seat : mineSeats) {
            String rowLabel = String.valueOf((char) ('A' + seat[0]));
            int seatNum = seat[1] + 1;
            selectionListCol.add(selectedSeatRow(rowLabel, seatNum, seat[0], seat[1]));
        }

        selectionListCol.add(Lk.divider());

        int totalCents = mineSeats.size() * SEAT_PRICE_CENTS;
        LkRow totalRow = new LkRow().justify("space-between");
        totalRow.add(Lk.muted(mineSeats.size() + " seat" + (mineSeats.size() == 1 ? "" : "s")));
        Span totalDisplay = new Span();
        totalDisplay.getElement().setProperty("innerHTML",
            "<b style='font-size:16px'>" + formatPrice(totalCents) + "</b>");
        totalRow.add(totalDisplay);
        selectionListCol.add(totalRow);

        LkBtn addBtn = new LkBtn("Add to cart →")
            .variant(LkBtn.Variant.primary)
            .size(LkBtn.Size.l)
            .full()
            .onClick(e -> addSelectionToCart(mineSeats));
        selectionListCol.add(addBtn);
    }

    private Component selectedSeatRow(String rowLabel, int seatNum, int rIdx, int cIdx) {
        Div row = new Div();
        row.addClassName("vz-selseat");

        Div info = new Div();
        Span name = new Span();
        name.getElement().setProperty("innerHTML", "<b>Row " + rowLabel + " · Seat " + seatNum + "</b>");
        Span subline = Lk.muted("Lower L · " + formatPrice(SEAT_PRICE_CENTS));
        subline.getStyle().set("font-size", "12.5px").set("display", "block");
        info.add(name, subline);

        Span remove = new Span();
        remove.addClassName("vz-selseat-x");
        remove.add(new LkIcon("close", 15));
        remove.getElement().addEventListener("click", e -> {
            seatStates[rIdx][cIdx] = '.';
            renderSeatGrid();
            updateSelectionRail();
        });

        row.add(info, remove);
        return row;
    }

    private void addSelectionToCart(List<int[]> mineSeats) {
        List<String> seatIds = new ArrayList<>();
        for (int[] seat : mineSeats) {
            String rowLabel = String.valueOf((char) ('A' + seat[0]));
            int seatNum = seat[1] + 1;
            seatIds.add("Row " + rowLabel + " Seat " + seatNum);
            seatStates[seat[0]][seat[1]] = '.';
        }
        try {
            InventorySelectionDTO selection = InventorySelectionDTO.seated(seatIds);
            callReserveService(selection);
            renderSeatGrid();
            updateSelectionRail();
            Toasts.success(mineSeats.size() + " seat" + (mineSeats.size() == 1 ? "" : "s") + " reserved — continue to cart.");
            UI.getCurrent().navigate(CartView.class);
        } catch (Exception ex) {
            Toasts.failure("Could not reserve seats: " + ex.getMessage());
        }
    }

    private List<int[]> collectMine() {
        List<int[]> mine = new ArrayList<>();
        for (int r = 0; r < seatStates.length; r++) {
            for (int c = 0; c < seatStates[r].length; c++) {
                if (seatStates[r][c] == 'm') mine.add(new int[]{r, c});
            }
        }
        return mine;
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
                    InventorySelectionDTO selection = InventorySelectionDTO.standing(2);
                    callReserveService(selection);
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
        grid.add(stateCell(VkSeat.State.free, "", "Available", "Tap to hold"));
        grid.add(stateCell(VkSeat.State.mine, "14", "In your order", "Locked 10 min"));
        grid.add(stateCell(VkSeat.State.held, "14", "Locked by others", "Live update"));
        grid.add(stateCell(VkSeat.State.sold, "14", "Sold", "Unavailable"));
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
        VkSeat seat = new VkSeat(state, num);
        Div meta = new Div();
        Span t = new Span();
        t.getElement().setProperty("innerHTML", "<b style='font-size:13.5px'>" + title + "</b>");
        Span s = Lk.muted(sub);
        s.getStyle().set("font-size", "12px").set("display", "block");
        meta.add(t, s);
        cell.add(seat, meta);
        return cell;
    }

    private void callReserveService(InventorySelectionDTO selection) {
        String token = AuthSession.token();
        if (token != null) {
            reservationService.reserveForMember(token, eventId, zoneId, selection);
        } else {
            reservationService.reserveForGuest(resolveGuestSessionId(), eventId, zoneId, selection);
        }
    }

    private String resolveGuestSessionId() {
    return GuestSession.sessionId();
}

    private static String formatPrice(int cents) {
        return "$" + (cents / 100) + "." + String.format("%02d", cents % 100);
    }
}
