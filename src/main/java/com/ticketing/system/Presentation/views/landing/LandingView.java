package com.ticketing.system.Presentation.views.landing;

import com.ticketing.system.Presentation.components.buyer.BzPoster;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.ticketing.system.Presentation.views.catalog.BrowseEventsView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Site root — marketing landing. Hero CTA → Browse, category quick
 * links, featured posters, "Why TicketHub" feature row. Reached at
 * {@code /} or by clicking the brand in the top bar.
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("TicketHub · Find your next experience")
@AnonymousAllowed
public class LandingView extends LkPage {

    public LandingView() {
        add(buildHero());
        add(Lk.h2("Browse by category"));
        add(buildCategoryGrid());
        add(Lk.h2("Featured this week"));
        add(buildFeaturedPosters());
        add(Lk.h2("Why TicketHub"));
        add(buildFeatures());
    }

    // ---- hero ----

    private Component buildHero() {
        Div hero = new Div();
        hero.addClassName("bz-hero");
        hero.getStyle().set("padding", "clamp(38px, 6vw, 64px) clamp(22px, 4vw, 56px)");

        Div title = new Div();
        title.addClassName("bz-hero-title");
        title.getStyle().set("font-size", "clamp(1.8rem, 4vw, 2.6rem)");
        title.setText("Find your next unforgettable experience.");

        Div sub = new Div();
        sub.addClassName("bz-hero-sub");
        sub.setText("Concerts, sports, theatre, conferences — across Israel, all in one place.");

        Div search = new Div();
        search.addClassName("bz-hero-search");
        search.add(new LkIcon("search", 17), new Span(" Search events, artists, venues…"));

        LkBtn ctaBrowse = new LkBtn("Browse events")
            .variant(LkBtn.Variant.primary)
            .icon(new LkIcon("ticket", 16))
            .onClick(e -> UI.getCurrent().navigate(BrowseEventsView.class));
        ctaBrowse.getStyle()
            .set("background", "#fff").set("color", "#1a5490")
            .set("border", "none").set("padding", "12px 22px").set("font-weight", "700")
            .set("box-shadow", "0 4px 14px rgba(0,0,0,0.18)");

        LkBtn ctaSell = new LkBtn("Sell on TicketHub")
            .variant(LkBtn.Variant.secondary)
            .icon(new LkIcon("building", 16))
            .onClick(e -> UI.getCurrent().navigate("login"));
        ctaSell.getStyle()
            .set("background", "transparent").set("color", "#fff")
            .set("border", "1.5px solid rgba(255,255,255,0.6)");

        Div ctaRow = new Div();
        ctaRow.getStyle()
            .set("display", "flex").set("gap", "10px").set("flex-wrap", "wrap")
            .set("margin-top", "18px");
        ctaRow.add(ctaBrowse, ctaSell);

        hero.add(title, sub, search, ctaRow);
        return hero;
    }

    // ---- category quick-pick grid ----

    private Component buildCategoryGrid() {
        Div grid = new Div();
        grid.getStyle()
            .set("display", "grid")
            .set("grid-template-columns", "repeat(auto-fit, minmax(min(100%, 220px), 1fr))")
            .set("gap", "16px");
        grid.add(
            categoryCard("Concerts",     "music",   "42 events",  "#8b5cf6", "#ec4899"),
            categoryCard("Sports",       "trophy",  "28 events",  "#0ea5e9", "#10b981"),
            categoryCard("Theatre",      "theater", "14 events",  "#dc2626", "#f59e0b"),
            categoryCard("Conferences",  "mic",     "9 events",   "#0d9488", "#1d4ed8")
        );
        return grid;
    }

    private Div categoryCard(String name, String iconName, String count, String g1, String g2) {
        Div card = new Div();
        card.getStyle()
            .set("background", "linear-gradient(135deg, " + g1 + ", " + g2 + ")")
            .set("border-radius", "12px").set("padding", "22px")
            .set("color", "#fff").set("cursor", "pointer")
            .set("box-shadow", "0 4px 14px -6px rgba(15,23,42,0.25)")
            .set("display", "flex").set("flex-direction", "column").set("gap", "10px");

        Div iconWrap = new Div();
        iconWrap.getStyle()
            .set("width", "44px").set("height", "44px")
            .set("background", "rgba(255,255,255,0.18)")
            .set("border-radius", "10px")
            .set("display", "flex").set("align-items", "center").set("justify-content", "center");
        iconWrap.add(new LkIcon(iconName, 22));

        Span title = new Span(name);
        title.getStyle().set("font-size", "1.1rem").set("font-weight", "800");

        Span sub = new Span(count);
        sub.getStyle().set("font-size", "0.82rem").set("opacity", "0.9");

        card.add(iconWrap, title, sub);
        // Pre-select the category in Browse's filter via query parameter.
        card.getElement().addEventListener("click", e ->
            UI.getCurrent().navigate("browse?category=" + name));
        return card;
    }

    // ---- featured posters ----

    private Component buildFeaturedPosters() {
        Div grid = new Div();
        grid.addClassName("bz-poster-grid");
        grid.add(
            new BzPoster("Concert",    "Coldplay Live in Tel Aviv",   "Park HaYarkon · 26 Jun · 20:00",  "From $250")
                .onClick(() -> UI.getCurrent().navigate("events/coldplay")),
            new BzPoster("Sport",      "Hapoel TLV vs Maccabi Haifa", "Bloomfield · 28 Jun · 21:00",     "From $80")
                .onClick(() -> UI.getCurrent().navigate("events/hapoel-tlv")),
            new BzPoster("Theatre",    "Othello at Habima",           "Habima Theatre · 30 Jun",         "From $120")
                .onClick(() -> UI.getCurrent().navigate("events/othello")),
            new BzPoster("Conference", "Spring AI Conference 2026",   "David InterContinental · 12 Jul", "From $400")
                .onClick(() -> UI.getCurrent().navigate("events/spring-ai-2026"))
        );
        return grid;
    }

    // ---- "Why TicketHub" features ----

    private Component buildFeatures() {
        Div grid = new Div();
        grid.getStyle()
            .set("display", "grid")
            .set("grid-template-columns", "repeat(auto-fit, minmax(min(100%, 240px), 1fr))")
            .set("gap", "16px");
        grid.add(
            featureCard("lock",       "Secure & instant",
                "Pay securely and your tickets land in your account in seconds."),
            featureCard("ticket",     "Verified events",
                "Every event on TicketHub is vetted — no scalpers, no surprises."),
            featureCard("comment",    "24/7 support",
                "Real humans, real answers. Reach us by chat or email any time.")
        );
        return grid;
    }

    private Div featureCard(String iconName, String title, String desc) {
        Div card = new Div();
        card.getStyle()
            .set("background", "#fff")
            .set("border", "1px solid var(--border)")
            .set("border-radius", "12px")
            .set("padding", "22px")
            .set("box-shadow", "0 1px 3px rgba(15,23,42,0.04)");

        Div iconWrap = new Div();
        iconWrap.getStyle()
            .set("width", "44px").set("height", "44px")
            .set("border-radius", "10px")
            .set("background", "rgba(26,84,144,0.10)")
            .set("color", "var(--primary)")
            .set("display", "flex").set("align-items", "center").set("justify-content", "center")
            .set("margin-bottom", "12px");
        iconWrap.add(new LkIcon(iconName, 22));

        Span tSpan = new Span(title);
        tSpan.getStyle()
            .set("font-size", "1.05rem").set("font-weight", "800").set("color", "#0f172a")
            .set("display", "block").set("margin-bottom", "6px");

        Span dSpan = new Span(desc);
        dSpan.getStyle()
            .set("font-size", "0.88rem").set("color", "var(--muted)")
            .set("line-height", "1.5").set("display", "block");

        card.add(iconWrap, tSpan, dSpan);
        return card;
    }
}
