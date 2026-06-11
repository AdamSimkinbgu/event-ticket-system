package com.ticketing.system.Presentation.views.order;

import java.util.List;

import com.ticketing.system.Core.Application.dto.ActiveOrderDTO;
import com.ticketing.system.Core.Application.dto.InventorySelectionDTO;
import com.ticketing.system.Core.Application.services.ReservationService;
import com.ticketing.system.Presentation.components.Toasts;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBanner;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkCol;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route(value = "cart", layout = MainLayout.class)
@PageTitle("Cart · TicketHub")
@AnonymousAllowed
public class CartView extends LkPage {

    private ActiveOrderDTO activeOrder;
    private final ReservationService reservationService;

    private LkCol linesCol;
    private Span sub, total, subText;
    private LkBtn proceedBtn;

    public CartView(ReservationService reservationService) {
        this.reservationService = reservationService;

        String userToken = (String) VaadinSession.getCurrent().getAttribute("userToken");
        this.activeOrder = reservationService.viewMyActiveOrder(userToken);

        title("Your cart");
        renderHeaderSubtitle();
        add(buildSplit());
    }

    private Component buildSplit() {
        Div split = new Div();
        split.addClassName("bz-cart-split");
        split.add(buildLineColumn(), buildSummaryCard());
        return split;
    }

    private Component buildLineColumn() {
        linesCol = new LkCol().gap(12);

        if (activeOrder != null) {
            linesCol.add(buildCountdownBanner(activeOrder.remainingSecondsBeforeExpiry()));
        }

        if (activeOrder == null || activeOrder.lines().isEmpty()) {
            linesCol.add(buildEmptyState());
        } else {
            for (ActiveOrderDTO.CartLineDTO line : activeOrder.lines()) {
                linesCol.add(cartLineFromDTO(line));
            }
        }

        return linesCol;
    }

    private Component cartLineFromDTO(ActiveOrderDTO.CartLineDTO line) {
        Div lineDiv = new Div();
        lineDiv.addClassName("bz-cartline");

        Div info = new Div();
        info.addClassName("bz-cartline-info");
        Span titleSpan = new Span();
        titleSpan.getElement().setProperty("innerHTML", "<b>" + line.eventName() + "</b>");
        Span seatLine = Lk.muted(line.seatNumber());
        info.add(titleSpan, seatLine);

        NativeButton removeBtn = new NativeButton("Remove");
        removeBtn.addClassName("bz-link");
        removeBtn.getStyle()
            .set("background", "none").set("border", "none").set("padding", "0").set("cursor", "pointer");
        removeBtn.addClickListener(e -> {
            String userToken = (String) VaadinSession.getCurrent().getAttribute("userToken");

            InventorySelectionDTO selection;
            if (line.seatNumber() != null) {
                selection = InventorySelectionDTO.seated(List.of(line.seatNumber()));
            } else {
                selection = InventorySelectionDTO.standing(1);
            }

         
            reservationService.removeLine(userToken, line.eventId(), line.zoneId(), selection);

            activeOrder = reservationService.viewMyActiveOrder(userToken);
            linesCol.remove(lineDiv);

            if (activeOrder == null || activeOrder.lines().isEmpty()) {
                linesCol.add(buildEmptyState());
            }

            renderHeaderSubtitle();
            renderTotals();
            Toasts.success("Removed from cart.");
        });
        info.add(removeBtn);

        lineDiv.add(info);

        Div priceTag = new Div();
        priceTag.addClassName("bz-cartline-price");
        priceTag.setText(formatPrice(line.pricePerTicket()));
        lineDiv.add(priceTag);

        return lineDiv;
    }

    private Component buildCountdownBanner(long remainingSeconds) {
        LkBanner banner = new LkBanner();
        banner.setIcon(new LkIcon("clock", 17));
        Span timer = new Span();
        timer.setText("Time left: " + remainingSeconds / 60 + ":" + remainingSeconds % 60);
        timer.getStyle().set("font-size", "16px").set("font-weight", "700");

        timer.getElement().executeJs(
            "const t = this;" +
            "let s = $0;" +
            "function tick(){" +
            "  if(s <= 0) return;" +
            "  const m = Math.floor(s/60); const sec = s%60;" +
            "  t.textContent = 'Time left: ' + m + ':' + (sec<10?'0'+sec:sec);" +
            "  s--;" +
            "  setTimeout(tick,1000);" +
            "}" +
            "tick();",
            remainingSeconds
        );

        banner.setBody(timer);
        return banner;
    }

    private Component buildEmptyState() {
        Span s = new Span("Your cart is empty. Browse events to add tickets.");
        s.getStyle()
            .set("display", "block").set("padding", "32px").set("text-align", "center")
            .set("color", "var(--muted)").set("background", "#fff")
            .set("border", "1px dashed var(--border-strong)").set("border-radius", "12px");
        return s;
    }

    private void renderHeaderSubtitle() {
        int n = (activeOrder != null) ? activeOrder.lines().size() : 0;
        subtitle(n == 0
            ? "No reservations held"
            : n + " reservation" + (n == 1 ? "" : "s") + " held · each line expires individually");
    }

    private Component buildSummaryCard() {
        LkCard card = new LkCard("Summary").pad(18);
        card.getStyle().set("align-self", "start").set("position", "sticky").set("top", "12px");

        LkCol col = new LkCol().gap(10);

        sub = new Span();
        total = new Span();
        subText = new Span();
        renderTotals();

        col.add(summaryRow("Subtotal", sub));
        col.add(Lk.divider());
        col.add(totalRow("Total", total));

        proceedBtn = new LkBtn("Proceed to checkout →")
            .variant(LkBtn.Variant.primary)
            .size(LkBtn.Size.l)
            .full()
            .onClick(e -> UI.getCurrent().navigate(CheckoutView.class));

        col.add(proceedBtn);
        col.add(subText);
        card.add(col);
        return card;
    }

    private void renderTotals() {
        int subtotal = activeOrder != null ? (int) activeOrder.currentTotalPrice() : 0;
        boolean empty = activeOrder == null || activeOrder.lines().isEmpty();
        int totalCents = empty ? 0 : subtotal;

        if (sub != null) sub.setText(formatPrice(subtotal));
        if (total != null) total.getElement().setProperty("innerHTML",
            "<b style='font-size:17px'>" + formatPrice(totalCents) + "</b>");

        if (subText != null) {
            subText.setText(empty
                ? "Add tickets from Browse to start an order."
                : "One 10-min timer covers the whole order at checkout");
            subText.getStyle()
                .set("text-align", "center").set("font-size", "12.5px")
                .set("color", "var(--muted)").set("display", "block");
        }

        if (proceedBtn != null) {
            proceedBtn.getElement().setEnabled(!empty);
            proceedBtn.getStyle().set("opacity", empty ? "0.55" : "1");
        }
    }

    private Component summaryRow(String label, Component value) {
        LkRow r = new LkRow().justify("space-between");
        r.add(Lk.muted(label), value);
        return r;
    }

    private Component totalRow(String label, Component value) {
        LkRow r = new LkRow().justify("space-between");
        Span l = new Span(); l.getElement().setProperty("innerHTML", "<b style='font-size:17px'>" + label + "</b>");
        r.add(l, value);
        return r;
    }

    private static String formatPrice(double cents) {
        return "$" + (cents / 100) + "." + String.format("%02d", (int)cents % 100);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}