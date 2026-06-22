package com.ticketing.system.Presentation.views.catalog;

import com.ticketing.system.Core.Application.dto.InventorySelectionDTO;
import com.ticketing.system.Core.Application.dto.InventoryZoneDTO;
import com.ticketing.system.Core.Application.dto.SeatDTO;
import com.ticketing.system.Core.Application.dto.VenueMapDTO;
import com.ticketing.system.Core.Application.services.CatalogService;
import com.ticketing.system.Core.Application.services.ReservationService;
import com.ticketing.system.Core.Domain.events.ZoneType;
import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBanner;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkCol;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.components.venue.VkQuantitySelector;
import com.ticketing.system.Presentation.components.venue.VkSeat;
import com.ticketing.system.Presentation.components.venue.VkSeatLegend;
import com.ticketing.system.Presentation.components.venue.VkSeatedZonePicker;
import com.ticketing.system.Presentation.components.venue.VkStandingZone;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.session.AuthSession;
import com.ticketing.system.Presentation.views.order.CartView;
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
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Zone picker (V2-CAT-03 / V2-RES-01 / V2-RES-02). Loads the selected zone and
 * renders the picker that matches its <b>type</b> — {@link VkSeatedZonePicker} for
 * a SEATED zone, {@link VkQuantitySelector} for a STANDING zone — both fed with
 * real {@code getEventVenueMap} data. New zone types are a compile error in
 * {@link #buildBody} until handled (exhaustive switch on {@link ZoneType}).
 */
@Route(value = "events/:eventId/seats/:zoneId", layout = MainLayout.class)
@PageTitle("Pick tickets · TicketHub")
@AnonymousAllowed
public class SeatPickerView extends LkPage implements BeforeEnterObserver {

    /** Pixel pitch between seat tiles in the absolute-positioned picker canvas. */
    private static final int SEAT_STEP = 34;

    private final CatalogService catalogService;
    private final ReservationService reservationService;

    private int eventId;
    private int zoneId;
    private InventoryZoneDTO zone;
    private double zonePrice;

    // Seated
    private VkSeatedZonePicker seatPicker;
    private LkCol selectionListCol;

    // Standing
    private VkQuantitySelector qtySelector;
    private LkBtn holdButton;

    private final Div bodyHolder = new Div();

    public SeatPickerView(CatalogService catalogService, ReservationService reservationService) {
        this.catalogService = catalogService;
        this.reservationService = reservationService;
        add(bodyHolder);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        this.eventId = parseId(event.getRouteParameters().get("eventId").orElse("0"));
        this.zoneId = parseId(event.getRouteParameters().get("zoneId").orElse("0"));
        loadAndBuild();
    }

    private void loadAndBuild() {
        bodyHolder.removeAll();
        InventoryZoneDTO loaded;
        try {
            VenueMapDTO map = catalogService.getEventVenueMap(credential(), eventId);
            loaded = map.inventoryZones().stream()
                    .filter(z -> z.getId() == zoneId).findFirst().orElse(null);
        } catch (RuntimeException ex) {
            bodyHolder.add(infoBanner("Could not load this zone: " + ex.getMessage()));
            return;
        }
        if (loaded == null) {
            bodyHolder.add(infoBanner("That zone is no longer available."));
            return;
        }
        this.zone = loaded;
        this.zonePrice = loaded.getPrice();
        bodyHolder.add(buildBody());
    }

    private Component buildBody() {
        return switch (ZoneType.valueOf(zone.getZoneType())) {
            case SEATED -> buildSeatedPicker();
            case STANDING -> buildStandingPicker();
        };
    }

    // ============================ SEATED ============================

    private Component buildSeatedPicker() {
        seatPicker = new VkSeatedZonePicker(buildSeatModels(), this::updateSelectionRail);

        Div wrap = new Div();
        wrap.add(buildBreadcrumb(zone.getName() + " · seated"));

        Div split = new Div();
        split.addClassName("vz-split");
        split.add(buildSeatCanvas(), buildSelectionRail());
        wrap.add(split);

        Div ref = new Div();
        ref.addClassName("vz-split2");
        ref.add(buildLockingExplainerCard(), buildStatesReferenceCard());
        wrap.add(ref);
        return wrap;
    }

    /** Builds the picker's seat models from real seats, laid out as a uniform grid. */
    private List<VkSeatedZonePicker.SeatModel> buildSeatModels() {
        TreeMap<String, List<SeatDTO>> byRow = new TreeMap<>();
        for (SeatDTO s : zone.getSeats()) {
            byRow.computeIfAbsent(rowOf(s.label()), k -> new ArrayList<>()).add(s);
        }
        List<VkSeatedZonePicker.SeatModel> models = new ArrayList<>();
        int rowIdx = 0;
        for (List<SeatDTO> rowSeats : byRow.values()) {
            rowSeats.sort(Comparator.comparingInt(s -> seatNumber(s.label())));
            int colIdx = 0;
            for (SeatDTO s : rowSeats) {
                models.add(new VkSeatedZonePicker.SeatModel(
                        s.label(), colIdx * SEAT_STEP, rowIdx * SEAT_STEP, stateFor(s.status())));
                colIdx++;
            }
            rowIdx++;
        }
        return models;
    }

    private static VkSeat.State stateFor(String status) {
        return switch (status) {
            case "AVAILABLE" -> VkSeat.State.free;
            case "SOLD" -> VkSeat.State.sold;
            default -> VkSeat.State.held;   // RESERVED / anything held by someone else
        };
    }

    private Component buildSeatCanvas() {
        LkCard card = new LkCard().pad(0);

        Div header = new Div();
        header.addClassName("vz-canvas-h");
        Span name = new Span();
        name.getElement().setProperty("innerHTML", "<b>" + escape(zone.getName()) + "</b>");
        Span sub = Lk.muted(" · click a green seat to select");
        sub.getStyle().set("font-size", "13px");
        Div title = new Div();
        title.add(name, sub);
        header.add(title);
        card.add(header);

        Div canvas = new Div();
        canvas.addClassName("vz-canvas");
        Div stageStrip = new Div();
        stageStrip.addClassName("vz-stage-strip");
        stageStrip.setText("◄ FACING STAGE ►");
        canvas.add(stageStrip);

        Div seatScroll = new Div();
        seatScroll.addClassName("vz-seatscroll");
        seatScroll.add(seatPicker);
        canvas.add(seatScroll);

        Div legendWrap = new Div();
        legendWrap.getStyle().set("padding", "0 18px 16px");
        legendWrap.add(new VkSeatLegend());
        canvas.add(legendWrap);

        card.add(canvas);
        return card;
    }

    private Component buildSelectionRail() {
        LkCol rail = new LkCol().gap(14);
        LkCard selection = new LkCard("Your selection").pad(16);
        selectionListCol = new LkCol().gap(10);
        selection.add(selectionListCol);
        rail.add(selection);
        updateSelectionRail();
        return rail;
    }

    private void updateSelectionRail() {
        if (selectionListCol == null) {
            return;
        }
        selectionListCol.removeAll();
        List<String> labels = seatPicker.hasSelection()
                ? seatPicker.getSelection().getSeatNumbers() : List.of();
        if (labels.isEmpty()) {
            Span hint = Lk.muted("Click a green seat above to select.");
            hint.getStyle().set("text-align", "center").set("display", "block").set("padding", "8px 0");
            selectionListCol.add(hint);
            return;
        }
        for (String label : labels) {
            selectionListCol.add(selectedSeatRow(label));
        }
        selectionListCol.add(Lk.divider());

        double total = labels.size() * zonePrice;
        LkRow totalRow = new LkRow().justify("space-between");
        totalRow.add(Lk.muted(labels.size() + " seat" + (labels.size() == 1 ? "" : "s")));
        Span totalDisplay = new Span();
        totalDisplay.getElement().setProperty("innerHTML", "<b style='font-size:16px'>" + money(total) + "</b>");
        totalRow.add(totalDisplay);
        selectionListCol.add(totalRow);

        selectionListCol.add(new LkBtn("Add to cart →")
            .variant(LkBtn.Variant.primary).size(LkBtn.Size.l).full()
            .onClick(e -> reserveSeated()));
    }

    private Component selectedSeatRow(String label) {
        Div row = new Div();
        row.addClassName("vz-selseat");
        Div info = new Div();
        Span name = new Span();
        name.getElement().setProperty("innerHTML", "<b>Seat " + escape(label) + "</b>");
        Span subline = Lk.muted(zone.getName() + " · " + money(zonePrice));
        subline.getStyle().set("font-size", "12.5px").set("display", "block");
        info.add(name, subline);

        Span remove = new Span();
        remove.addClassName("vz-selseat-x");
        remove.add(new LkIcon("close", 15));
        // picker.deselect() fires onSelectionChange → updateSelectionRail()
        remove.getElement().addEventListener("click", e -> seatPicker.deselect(label));
        row.add(info, remove);
        return row;
    }

    private void reserveSeated() {
        InventorySelectionDTO selection = seatPicker.getSelection();
        if (selection == null) {
            return;
        }
        int count = selection.getSeatNumbers().size();
        try {
            callReserveService(selection);
            seatPicker.clearSelection();
            Toasts.success(count + " seat" + (count == 1 ? "" : "s") + " reserved — continue to cart.");
            UI.getCurrent().navigate(CartView.class);
        } catch (Exception ex) {
            Toasts.failure("Could not reserve seats: " + ex.getMessage());
        }
    }

    // ============================ STANDING ============================

    private Component buildStandingPicker() {
        Div wrap = new Div();
        wrap.add(buildBreadcrumb(zone.getName() + " · standing"));

        LkCard card = new LkCard("General Admission · standing").pad(18);
        LkCol col = new LkCol().gap(16);
        col.add(new VkStandingZone(zone.getName(), zone.getSoldAmount(), zone.getCapacity(),
                money(zonePrice) + " each"));

        qtySelector = new VkQuantitySelector(
                Math.max(0, zone.getAvailableAmount()),
                (int) Math.round(zonePrice * 100),
                this::updateHoldButton);
        col.add(qtySelector);
        col.add(Lk.divider());

        holdButton = new LkBtn("Hold tickets →").variant(LkBtn.Variant.primary).full()
                .onClick(e -> reserveStanding());
        col.add(holdButton);
        updateHoldButton();

        card.add(col);
        wrap.add(card);
        return wrap;
    }

    private void updateHoldButton() {
        if (holdButton == null) {
            return;
        }
        int q = qtySelector.getQuantity();
        holdButton.setEnabled(qtySelector.hasSelection());
        holdButton.setText(qtySelector.hasSelection()
                ? "Hold " + q + " ticket" + (q == 1 ? "" : "s") + " →"
                : "Sold out");
    }

    private void reserveStanding() {
        InventorySelectionDTO selection = qtySelector.getSelection();
        if (selection == null) {
            Toasts.failure("No tickets available.");
            return;
        }
        int q = qtySelector.getQuantity();
        try {
            callReserveService(selection);
            Toasts.success(q + " ticket" + (q == 1 ? "" : "s") + " reserved — continue to cart.");
            UI.getCurrent().navigate(CartView.class);
        } catch (Exception ex) {
            Toasts.failure("Could not reserve tickets: " + ex.getMessage());
        }
    }

    // ============================ shared ============================

    private Component buildBreadcrumb(String trailText) {
        Div crumb = new Div();
        crumb.addClassName("vz-crumb");
        Span back = new Span("← Back to areas");
        back.addClassName("bz-link");
        back.getElement().addEventListener("click", e -> UI.getCurrent().navigate("events/" + eventId));
        Span trail = Lk.muted(" / " + trailText + " · " + money(zonePrice));
        trail.getStyle().set("font-size", "13px");
        crumb.add(back, trail);
        return crumb;
    }

    private Component buildLockingExplainerCard() {
        LkCard card = new LkCard("How locking works").pad(16);
        LkCol col = new LkCol().gap(9);
        col.add(lockLegendRow("mine", "<b>In your order</b> — held for you for 10 minutes."));
        col.add(lockLegendRow("held", "<b>Locked by others</b> — another buyer is mid-purchase."));
        col.add(lockLegendRow("sold", "<b>Sold</b> — already purchased."));
        Span fine = Lk.muted("Two buyers can never hold the same seat — first tap wins.");
        fine.getStyle().set("font-size", "12px").set("margin-top", "2px").set("display", "block");
        col.add(fine);
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

    private Component buildStatesReferenceCard() {
        LkCard card = new LkCard("Race-condition states")
            .subtitle("What the seat colours mean under load")
            .pad(18);
        Div grid = new Div();
        grid.addClassName("vz-state-grid");
        grid.add(stateCell(VkSeat.State.free, "", "Available", "Tap to hold"));
        grid.add(stateCell(VkSeat.State.mine, "12", "In your order", "Locked 10 min"));
        grid.add(stateCell(VkSeat.State.held, "12", "Locked by others", "Live update"));
        grid.add(stateCell(VkSeat.State.sold, "12", "Sold", "Unavailable"));
        card.add(grid);
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

    private String credential() {
        String token = AuthSession.token();
        return token != null ? token : resolveGuestSessionId();
    }

    private String resolveGuestSessionId() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null) {
            return UUID.randomUUID().toString();
        }
        String guestId = (String) session.getAttribute("guestSessionId");
        if (guestId == null) {
            guestId = UUID.randomUUID().toString();
            session.setAttribute("guestSessionId", guestId);
        }
        return guestId;
    }

    private Component infoBanner(String message) {
        LkBanner banner = new LkBanner();
        banner.tone(LkBanner.Tone.info);
        banner.setIcon(new LkIcon("info", 18));
        banner.setBody(new Span(message));
        return banner;
    }

    private static String rowOf(String label) {
        int i = 0;
        while (i < label.length() && !Character.isDigit(label.charAt(i))) {
            i++;
        }
        return i > 0 ? label.substring(0, i) : label;
    }

    private static int seatNumber(String label) {
        int i = 0;
        while (i < label.length() && !Character.isDigit(label.charAt(i))) {
            i++;
        }
        try {
            return Integer.parseInt(label.substring(i));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int parseId(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String money(double v) {
        return String.format(java.util.Locale.US, "$%,.2f", v);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
