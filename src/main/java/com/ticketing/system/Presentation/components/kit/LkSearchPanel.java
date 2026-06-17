package com.ticketing.system.Presentation.components.kit;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;

import java.util.List;

/**
 * Search popover panel — recent-search chip row + grouped result lists.
 * Ports the React {@code SearchPanel}. Static {@link #defaults()}
 * factory returns the reference content.
 */
public class LkSearchPanel extends Div {

    public record Result(String title, String detail) { }
    public record Group(String label, String iconName, List<Result> items) { }

    public LkSearchPanel(List<String> recents, List<Group> groups) {
        addClassName("lk-searchpop");

        Div recentSect = new Div();
        recentSect.addClassName("lk-search-recent");
        recentSect.add(new LkMenu.Label("Recent searches"));
        Div chips = new Div();
        chips.addClassName("lk-recent-chips");
        for (String r : recents) {
            Span chip = new Span();
            chip.addClassName("lk-recent-chip");
            chip.add(new LkIcon("clock", 12));
            chip.add(new Span(r));
            chips.add(chip);
        }
        recentSect.add(chips);
        add(recentSect);

        for (Group g : groups) {
            Div sect = new Div();
            sect.addClassName("lk-search-group");
            sect.add(new LkMenu.Label(g.label));
            for (Result it : g.items) {
                NativeButton row = new NativeButton();
                row.addClassName("lk-search-res");
                Span ico = new Span();
                ico.addClassName("lk-search-res-ic");
                ico.add(new LkIcon(g.iconName, 16));
                Span body = new Span();
                body.addClassName("lk-search-res-body");
                Span t = new Span(it.title);  t.addClassName("lk-search-res-t");
                Span d = new Span(it.detail); d.addClassName("lk-search-res-d");
                body.add(t, d);
                LkIcon go = new LkIcon("arrowRight", 15);
                go.addClassName("lk-search-res-go");
                row.add(ico, body, go);
                sect.add(row);
            }
            add(sect);
        }
    }

    public static LkSearchPanel defaults() {
        return new LkSearchPanel(
            List.of("Coldplay", "Hapoel TLV", "Jazz festival"),
            List.of(
                new Group("Events", "ticket", List.of(
                    new Result("Coldplay · Music of the Spheres", "26 Jun · Park HaYarkon"),
                    new Result("Mashina · 35-Year Tour",          "5 Jul · TLV Convention Center")
                )),
                new Group("Artists", "mic", List.of(
                    new Result("Coldplay",   "Concert · 2 upcoming"),
                    new Result("Eden Hason", "Concert · 1 upcoming")
                )),
                new Group("Venues", "map", List.of(
                    new Result("Park HaYarkon",      "Tel Aviv · 6 events"),
                    new Result("Bloomfield Stadium", "Tel Aviv · 3 events")
                ))
            )
        );
    }
}
