package com.ticketing.system.Presentation.views.order;

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
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.ArrayList;
import java.util.List;

@Route(value = "cart", layout = MainLayout.class)
@PageTitle("Cart · TicketHub")
@AnonymousAllowed
public class CartView extends LkPage {

    private record CartItem(String title, String seat, int priceCents, String timer, boolean urgent, String gradient) { }

    private final List<CartItem> items = new ArrayList<>(List.of(
        new CartItem("Coldplay · Music of the Spheres", "Lower L · Row C · Seat 14", 16000, "08:41", false,
                     "linear-gradient(135deg, #8b5cf6, #ec4899)"),
        new CartItem("Coldplay · Music of the Spheres", "Lower L · Row C · Seat 15", 16000, "08:41", false,
                     "linear-gradient(135deg, #8b5cf6, #ec4899)"),
        new CartItem("Hapoel TLV vs Maccabi Haifa",     "2 × General Admission",     16000, "02:12", true,
                     "linear-gradient(135deg, #0ea5e9, #10b981)")
    ));

    private static final int SERVICE_FEE_CENTS = 2400;

    private LkCol linesCol;
    private Span sub, total, subText;
    private LkBtn proceedBtn;
    private Span emptyState;

    public CartView() {
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
        for (int i = 0; i < items.size(); i++) {
            linesCol.add(cartLine(items.get(i), i));
        }
        if (items.isEmpty()) linesCol.add(buildEmptyState());
        LkBanner heldBanner = new LkBanner(LkBanner.Tone.info, new LkIcon("lock", 17),
            "Tickets are held just for you. They release automatically when a timer runs out.");
        linesCol.add(heldBanner);
        return linesCol;
    }

    private Component cartLine(CartItem item, int index) {
        Div line = new Div();
        line.addClassName("bz-cartline");

        Div thumb = new Div();
        thumb.addClassName("bz-cartline-thumb");
        thumb.getStyle().set("background", item.gradient);
        line.add(thumb);

        Div info = new Div();
        info.addClassName("bz-cartline-info");
        Span title = new Span();
        title.getElement().setProperty("innerHTML", "<b>" + escape(item.title) + "</b>");
        Span seat = Lk.muted(item.seat);
        seat.getStyle().set("font-size", "13.5px");
        info.add(title, seat);

        LkRow controls = new LkRow().gap(10);
        controls.getStyle().set("margin-top", "4px");

        Span timerChip = new Span();
        timerChip.addClassName("bz-timer");
        if (item.urgent) timerChip.addClassName("urgent");
        timerChip.add(new LkIcon("clock", 13), new Span(" " + item.timer));

        NativeButton remove = new NativeButton("Remove");
        remove.addClassName("bz-link");
        remove.getStyle()
            .set("background", "none").set("border", "none").set("padding", "0").set("cursor", "pointer");
        remove.addClickListener(e -> removeItem(item, line));

        controls.add(timerChip, remove);
        info.add(controls);
        line.add(info);

        Div priceTag = new Div();
        priceTag.addClassName("bz-cartline-price");
        priceTag.setText(formatPrice(item.priceCents));
        line.add(priceTag);
        return line;
    }

    private void removeItem(CartItem item, Component lineElement) {
        items.remove(item);
        linesCol.remove(lineElement);
        if (items.isEmpty()) {
            emptyState = (Span) buildEmptyState();
            linesCol.getElement().insertChild(0, emptyState.getElement());
        }
        renderHeaderSubtitle();
        renderTotals();
        Toasts.success("Removed from cart.");
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
        if (items.isEmpty()) {
            subtitle("No reservations held");
        } else {
            subtitle(items.size() + " reservation" + (items.size() == 1 ? "" : "s") + " held · each line expires individually");
        }
    }

    // -------- summary card --------

    private Component buildSummaryCard() {
        LkCard card = new LkCard("Summary").pad(18);
        card.getStyle().set("align-self", "start").set("position", "sticky").set("top", "12px");

        LkCol col = new LkCol().gap(10);

        sub = new Span();
        total = new Span();
        subText = new Span();
        renderTotals();

        col.add(summaryRow("Subtotal", sub));
        col.add(summaryRow("Service fee", new Span(formatPrice(SERVICE_FEE_CENTS))));
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
        int subtotal = items.stream().mapToInt(CartItem::priceCents).sum();
        int totalCents = items.isEmpty() ? 0 : subtotal + SERVICE_FEE_CENTS;
        if (sub != null)   sub.setText(formatPrice(subtotal));
        if (total != null) total.getElement().setProperty("innerHTML", "<b style='font-size:17px'>" + formatPrice(totalCents) + "</b>");
        if (subText != null) {
            subText.setText(items.isEmpty()
                ? "Add tickets from Browse to start an order."
                : "One 10-min timer covers the whole order at checkout");
            subText.getStyle()
                .set("text-align", "center").set("font-size", "12.5px").set("color", "var(--muted)").set("display", "block");
        }
        if (proceedBtn != null) {
            proceedBtn.getElement().setEnabled(!items.isEmpty());
            proceedBtn.getStyle().set("opacity", items.isEmpty() ? "0.55" : "1");
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

    private static String formatPrice(int cents) {
        return "$" + (cents / 100) + "." + String.format("%02d", cents % 100);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
