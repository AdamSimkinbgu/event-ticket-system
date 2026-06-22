package com.ticketing.system.Presentation.views.order;

import com.ticketing.system.Core.Application.dto.CheckoutResultDTO;
import com.ticketing.system.Presentation.components.Money;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkGrid;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.views.account.MyAccountView;
import com.ticketing.system.Presentation.views.catalog.BrowseEventsView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.LinkedHashMap;
import java.util.Map;

@Route(value = "order-confirmed", layout = MainLayout.class)
@PageTitle("Order confirmed · TicketHub")
@AnonymousAllowed
public class OrderConfirmationView extends LkPage implements BeforeEnterObserver {

    private CheckoutResultDTO result;

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        VaadinSession s = VaadinSession.getCurrent();
        if (s == null) {
            result = null;
        } else {
            result = (CheckoutResultDTO) s.getAttribute("checkout.result");
            s.setAttribute("checkout.result", null);
        }
        if (result == null) {
            event.rerouteTo(BrowseEventsView.class);
            return;
        }
    }

    public OrderConfirmationView() {
        add(buildConfirmHero());
        add(buildTicketsCard());
        add(buildActions());
    }

    private Component buildConfirmHero() {
        Div hero = new Div();
        hero.addClassName("bz-confirm-hero");

        Div check = new Div();
        check.addClassName("bz-confirm-check");
        check.add(new LkIcon("check", 28, 2.4));

        Div text = new Div();
        H2 title = new H2("Your tickets are confirmed");
        title.getStyle().set("margin", "0").set("color", "#fff");

        String receiptDisplay = (result != null)
            ? "Receipt #TKT-" + String.format("%05d", result.orderReceiptId())
            : "Receipt pending";
        String totalDisplay = (result != null)
            ? Money.format(Money.toCents(result.totalCharged()))
            : "$0.00";
        String transactionDisplay = (result != null)
            ? String.format("%04x", result.paymentTransactionId())
            : "—";

        Span sub = new Span(receiptDisplay + " · Transaction " + transactionDisplay
            + " · " + totalDisplay + " paid · emailed to you");
        sub.getStyle().set("color", "rgba(255,255,255,0.9)").set("font-size", "14.5px");
        text.add(title, sub);

        hero.add(check, text);
        return hero;
    }

    private Component buildTicketsCard() {
        LkCard card = new LkCard("Your tickets").pad(0);

        LkGrid grid = new LkGrid()
            .col("Ticket ID",  "ticketId")
            .col("Barcode",    "barcode");

        if (result != null && result.issuedTicketIds() != null && !result.issuedTicketIds().isEmpty()) {
            for (int ticketId : result.issuedTicketIds()) {
                ticketRow(grid, ticketId);
            }
        } else {
            ticketRow(grid, 0);
        }

        grid.build();
        card.add(grid);
        return card;
    }

    private void ticketRow(LkGrid grid, int ticketId) {
        Map<String, Object> row = new LinkedHashMap<>();
        
        if (ticketId > 0) {
            row.put("ticketId", String.valueOf(ticketId));
            Span barcode = Lk.mono("▮▯▮▯▮ " + String.format("%04X-%04X", ticketId >> 16, ticketId & 0xFFFF));
            barcode.addClassName("bz-barcode");
            row.put("barcode", barcode);
        } else {
            row.put("ticketId", "—");
            row.put("barcode", "—");
        }
        
        grid.row(row);
    }

    private Component buildActions() {
        LkRow row = new LkRow().gap(10);
        row.add(new LkBtn("View my tickets")
            .variant(LkBtn.Variant.primary)
            .onClick(e -> UI.getCurrent().navigate(MyAccountView.class)));
        row.add(new LkBtn("Browse more events")
            .variant(LkBtn.Variant.secondary)
            .onClick(e -> UI.getCurrent().navigate(BrowseEventsView.class)));
        return row;
    }
}