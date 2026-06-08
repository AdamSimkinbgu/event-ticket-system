package com.ticketing.system.Presentation.views.catalog;

import com.ticketing.system.Presentation.components.buyer.BzPoster;
import com.ticketing.system.Presentation.components.kit.Lk;
import com.ticketing.system.Presentation.components.kit.LkBtn;
import com.ticketing.system.Presentation.components.kit.LkCard;
import com.ticketing.system.Presentation.components.kit.LkChip;
import com.ticketing.system.Presentation.components.kit.LkCol;
import com.ticketing.system.Presentation.components.kit.LkDateRangeField;
import com.ticketing.system.Presentation.components.kit.LkGrid;
import com.ticketing.system.Presentation.components.kit.LkIcon;
import com.ticketing.system.Presentation.components.kit.LkPage;
import com.ticketing.system.Presentation.components.kit.LkRow;
import com.ticketing.system.Presentation.components.kit.LkSelect;
import com.ticketing.system.Presentation.components.kit.LkStatusDot;
import com.ticketing.system.Presentation.layouts.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Route(value = "browse", layout = MainLayout.class)
@PageTitle("Browse Events · TicketHub")
@AnonymousAllowed
public class BrowseEventsView extends LkPage implements BeforeEnterObserver {

    // ---- data model ----

    private record EventRow(String name, String category, String venue, String region,
            String date, int dayOfYear, int priceCents,
            LkStatusDot.Tone tone, String status, String id) {
    }

    private static final List<EventRow> ALL_EVENTS = List.of(
            new EventRow("Coldplay · Music of the Spheres", "Concerts", "Park HaYarkon", "Tel Aviv", "26 Jun", 177,
                    25000, LkStatusDot.Tone.ok, "On sale", "coldplay"),
            new EventRow("Hapoel TLV vs Maccabi Haifa", "Sports", "Bloomfield Stadium", "Tel Aviv", "28 Jun", 179, 8000,
                    LkStatusDot.Tone.ok, "On sale", "hapoel-tlv"),
            new EventRow("Othello at Habima", "Theatre", "Habima Theatre", "Tel Aviv", "30 Jun", 181, 12000,
                    LkStatusDot.Tone.ok, "On sale", "othello"),
            new EventRow("Mashina · 35-Year Tour", "Concerts", "TLV Convention Center", "Tel Aviv", "5 Jul", 186, 18000,
                    LkStatusDot.Tone.warn, "Selling fast", "mashina"),
            new EventRow("Beitar Jerusalem vs Hapoel BS", "Sports", "Teddy Stadium", "Jerusalem", "7 Jul", 188, 6000,
                    LkStatusDot.Tone.ok, "On sale", "beitar-hapoel"),
            new EventRow("Spring AI Conference 2026", "Conferences", "David InterContinental", "Tel Aviv", "12 Jul",
                    193, 40000, LkStatusDot.Tone.warn, "Few left", "spring-ai-2026"),
            new EventRow("Eden Hason · Live", "Concerts", "Caesarea Amphitheatre", "Caesarea", "20 Jul", 201, 22000,
                    LkStatusDot.Tone.ok, "On sale", "eden-hason"),
            new EventRow("Beitar Jerusalem vs Maccabi TA", "Sports", "Teddy Stadium", "Jerusalem", "23 Jul", 204, 7500,
                    LkStatusDot.Tone.muted, "Pre-sale", "beitar-maccabi"));

    private static final String CAT_ALL = "All categories";
    private static final String REGION_ALL = "All regions";
    private static final String DATE_ANY = "Any time";
    private static final List<String> CATEGORIES = List.of(CAT_ALL, "Concerts", "Sports", "Theatre", "Conferences");
    private static final List<String> REGIONS = List.of(REGION_ALL, "Tel Aviv", "Jerusalem", "Haifa", "Caesarea",
            "Be'er Sheva");
    private static final List<String> SORTS = List.of(
            "Date · soonest", "Date · latest", "Price · low to high", "Price · high to low", "Popularity");

    // ---- filter state ----

    private String filterCategory = CAT_ALL;
    private String filterRegion = REGION_ALL;
    private String filterDateRange = DATE_ANY;
    private String filterSort = SORTS.get(0);
    private Integer filterPriceMin = null;
    private Integer filterPriceMax = null;

    // ---- ui references for re-render / reset ----

    private final Map<LkChip, String> chipToCategory = new LinkedHashMap<>();
    private LkSelect categorySelect;
    private LkSelect regionSelect;
    private LkSelect sortSelect;
    private LkDateRangeField dateRangeField;
    private IntegerField priceMin;
    private IntegerField priceMax;
    private Div gridSlot;
    private Span resultsCountSpan;

    public BrowseEventsView() {
        add(buildHero());
        add(buildCategoryChips());
        add(Lk.h2("Featured this week"));
        add(buildPosterGrid());
        add(buildAllEventsHeader());
        add(buildBrowseSplit());
        renderGrid();
    }

    /**
     * Apply a {@code ?category=...} query parameter (e.g. when arriving
     * from a category card on the landing page) by pre-selecting the
     * matching chip + sidebar Category select, and filtering the grid.
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        List<String> values = event.getLocation().getQueryParameters()
            .getParameters().getOrDefault("category", List.of());
        if (values.isEmpty()) return;
        String category = values.get(0);
        // Only honor a known category (else the chips/select would mismatch).
        if (CATEGORIES.contains(category)) setCategory(category);
    }

    // ---- hero ----

    private Component buildHero() {
        Div hero = new Div();
        hero.addClassName("bz-hero");
        Div title = new Div();
        title.addClassName("bz-hero-title");
        title.setText("Find your next experience");
        Div sub = new Div();
        sub.addClassName("bz-hero-sub");
        sub.setText("Concerts, matches, theatre and conferences — across Israel.");
        Div search = new Div();
        search.addClassName("bz-hero-search");
        search.add(new LkIcon("search", 17), new Span(" Search events, artists, venues…"));
        hero.add(title, sub, search);
        return hero;
    }

    // ---- category chips (synced with sidebar select) ----

    private Component buildCategoryChips() {
        LkRow row = new LkRow().gap(8);
        addChip(row, null, "All", CAT_ALL, true);
        addChip(row, "music", "Concerts", "Concerts", false);
        addChip(row, "trophy", "Sports", "Sports", false);
        addChip(row, "theater", "Theatre", "Theatre", false);
        addChip(row, "mic", "Conferences", "Conferences", false);
        row.getStyle().set("margin", "4px 0 2px");
        return row;
    }

    private void addChip(LkRow row, String iconName, String label, String category, boolean active) {
        LkChip chip = new LkChip(label);
        if (iconName != null)
            chip.getElement().insertChild(0, new LkIcon(iconName, 15).getElement());
        if (active)
            chip.active();
        chip.getElement().addEventListener("click", e -> setCategory(category));
        chipToCategory.put(chip, category);
        row.add(chip);
    }

    private void setCategory(String category) {
        if (filterCategory.equals(category))
            return;
        filterCategory = category;
        chipToCategory.forEach((chip, cat) -> chip.active(cat.equals(category)));
        if (categorySelect != null)
            categorySelect.setValue(category);
        renderGrid();
    }

    // ---- featured posters ----

    private Component buildPosterGrid() {
        Div grid = new Div();
        grid.addClassName("bz-poster-grid");
        grid.add(
                new BzPoster("Concert", "Coldplay Live in Tel Aviv", "Park HaYarkon · 26 Jun · 20:00", "From $250")
                        .onClick(() -> UI.getCurrent().navigate("events/coldplay")),
                new BzPoster("Sport", "Hapoel TLV vs Maccabi Haifa", "Bloomfield · 28 Jun · 21:00", "From $80")
                        .onClick(() -> UI.getCurrent().navigate("events/hapoel-tlv")),
                new BzPoster("Theatre", "Othello at Habima", "Habima Theatre · 30 Jun", "From $120")
                        .onClick(() -> UI.getCurrent().navigate("events/othello")),
                new BzPoster("Conference", "Spring AI Conference 2026", "David InterContinental · 12 Jul", "From $400")
                        .onClick(() -> UI.getCurrent().navigate("events/spring-ai-2026")));
        return grid;
    }

    // ---- "All events" header with live result count ----

    private Component buildAllEventsHeader() {
        LkRow row = new LkRow().align("baseline").gap(10);
        row.add(Lk.h2("All events"));
        resultsCountSpan = Lk.muted("");
        resultsCountSpan.getStyle().set("font-size", "13.5px");
        row.add(resultsCountSpan);
        return row;
    }

    // ---- filter sidebar + grid slot ----

    private Component buildBrowseSplit() {
        Div split = new Div();
        split.addClassName("bz-browse-split");
        split.add(buildFilters(), buildGridSlot());
        return split;
    }

    private Component buildFilters() {
        LkCard card = new LkCard("Filters").pad(18).flush();

        NativeButton clear = new NativeButton("Clear all");
        clear.addClassName("lk-link-btn");
        clear.addClickListener(e -> clearFilters());
        card.headerRight(clear);

        categorySelect = new LkSelect(CAT_ALL, CATEGORIES).label("Category");
        categorySelect.onChange(this::setCategory);

        dateRangeField = new LkDateRangeField().label("Date range");
        dateRangeField.onChange(v -> {
            filterDateRange = v;
            renderGrid();
        });

        regionSelect = new LkSelect(REGION_ALL, REGIONS).label("Region");
        regionSelect.onChange(v -> {
            filterRegion = v;
            renderGrid();
        });

        sortSelect = new LkSelect(SORTS.get(0), SORTS).label("Sort by");
        sortSelect.onChange(v -> {
            filterSort = v;
            renderGrid();
        });

        LkCol col = new LkCol().gap(16);
        col.add(categorySelect, dateRangeField, buildPriceRange(), regionSelect, sortSelect);
        card.add(col);
        return card;
    }

    private Component buildPriceRange() {
        Div wrap = new Div();
        Span label = new Span("Price range");
        label.addClassName("lk-label");
        label.getStyle().set("display", "block").set("margin-bottom", "6px");
        wrap.add(label);

        priceMin = new IntegerField();
        priceMin.setPlaceholder("Min");
        priceMin.setMin(0);
        priceMin.setMax(1000);
        priceMin.setStep(10);
        priceMin.setPrefixComponent(new Span("$"));
        priceMin.setValueChangeMode(ValueChangeMode.LAZY);
        priceMin.getStyle().set("flex", "1 1 0");
        priceMin.addValueChangeListener(e -> {
            filterPriceMin = e.getValue();
            renderGrid();
        });

        priceMax = new IntegerField();
        priceMax.setPlaceholder("Max");
        priceMax.setMin(0);
        priceMax.setMax(1000);
        priceMax.setStep(10);
        priceMax.setPrefixComponent(new Span("$"));
        priceMax.setValueChangeMode(ValueChangeMode.LAZY);
        priceMax.getStyle().set("flex", "1 1 0");
        priceMax.addValueChangeListener(e -> {
            filterPriceMax = e.getValue();
            renderGrid();
        });

        LkRow row = new LkRow().gap(8);
        row.add(priceMin, priceMax);
        wrap.add(row);
        return wrap;
    }

    private Component buildGridSlot() {
        gridSlot = new Div();
        gridSlot.getStyle().set("min-width", "0").set("width", "100%");
        return gridSlot;
    }

    // ---- reset ----

    private void clearFilters() {
        filterCategory = CAT_ALL;
        filterRegion = REGION_ALL;
        filterDateRange = DATE_ANY;
        filterSort = SORTS.get(0);
        filterPriceMin = null;
        filterPriceMax = null;

        chipToCategory.forEach((chip, cat) -> chip.active(cat.equals(CAT_ALL)));
        categorySelect.setValue(CAT_ALL);
        regionSelect.setValue(REGION_ALL);
        sortSelect.setValue(SORTS.get(0));
        dateRangeField.setValue(DATE_ANY);
        priceMin.clear();
        priceMax.clear();
        renderGrid();
    }

    // ---- filter + render ----

    private List<EventRow> applyFilters() {
        return ALL_EVENTS.stream()
                .filter(this::matchesCategory)
                .filter(this::matchesRegion)
                .filter(this::matchesDateRange)
                .filter(this::matchesPrice)
                .sorted(comparatorFor(filterSort))
                .toList();
    }

    private boolean matchesCategory(EventRow e) {
        return CAT_ALL.equals(filterCategory) || filterCategory.equals(e.category);
    }

    private boolean matchesRegion(EventRow e) {
        return REGION_ALL.equals(filterRegion) || filterRegion.equals(e.region);
    }

    private boolean matchesDateRange(EventRow e) {
        // Mock semantics — "today" anchored to day 177 (first event in the dataset)
        // so each preset actually changes the visible set.
        return switch (filterDateRange) {
            case "Today" -> e.dayOfYear == 177;
            case "This weekend" -> e.dayOfYear == 179 || e.dayOfYear == 180;
            case "Next 7 days" -> e.dayOfYear <= 183;
            case "Next 30 days" -> e.dayOfYear <= 206;
            case "This month" -> e.dayOfYear <= 181;
            case "Next 3 months" -> true;
            default -> true; // Any time
        };
    }

    private boolean matchesPrice(EventRow e) {
        int dollars = e.priceCents / 100;
        if (filterPriceMin != null && dollars < filterPriceMin)
            return false;
        if (filterPriceMax != null && dollars > filterPriceMax)
            return false;
        return true;
    }

    private static Comparator<EventRow> comparatorFor(String sort) {
        return switch (sort) {
            case "Date · latest" -> Comparator.comparingInt(EventRow::dayOfYear).reversed();
            case "Price · low to high" -> Comparator.comparingInt(EventRow::priceCents);
            case "Price · high to low" -> Comparator.comparingInt(EventRow::priceCents).reversed();
            case "Popularity" -> Comparator.comparing(EventRow::id);
            default -> Comparator.comparingInt(EventRow::dayOfYear); // soonest
        };
    }

    private void renderGrid() {
        List<EventRow> visible = applyFilters();
        gridSlot.removeAll();

        if (resultsCountSpan != null) {
            resultsCountSpan.setText(formatResultLine(visible.size()));
        }

        if (visible.isEmpty()) {
            gridSlot.add(buildEmptyState());
            return;
        }
        gridSlot.add(buildGrid(visible));
    }

    private String formatResultLine(int n) {
        StringBuilder s = new StringBuilder();
        s.append(n).append(" event").append(n == 1 ? "" : "s");
        if (!CAT_ALL.equals(filterCategory))
            s.append(" · ").append(filterCategory.toLowerCase());
        if (!REGION_ALL.equals(filterRegion))
            s.append(" in ").append(filterRegion);
        if (!DATE_ANY.equals(filterDateRange))
            s.append(" · ").append(filterDateRange.toLowerCase());
        if (filterPriceMin != null || filterPriceMax != null) {
            s.append(" · $")
                    .append(filterPriceMin == null ? "0" : filterPriceMin)
                    .append("–$")
                    .append(filterPriceMax == null ? "∞" : filterPriceMax);
        }
        return s.toString();
    }

    private Component buildGrid(List<EventRow> events) {
        LkGrid grid = new LkGrid()
                .col("Event", "name")
                .col("Category", "cat")
                .col("Venue", "venue")
                .col("Date", "date")
                .col("From", "from", LkGrid.Align.RIGHT)
                .col("Status", "status")
                .col("", "act", LkGrid.Align.RIGHT);
        for (EventRow ev : events)
            addRow(grid, ev);
        grid.build();
        return grid;
    }

    private void addRow(LkGrid grid, EventRow ev) {
        Map<String, Object> row = new LinkedHashMap<>();
        Span name = new Span();
        name.getElement().setProperty("innerHTML", "<b>" + escape(ev.name) + "</b>");
        row.put("name", name);
        row.put("cat", ev.category);
        row.put("venue", ev.venue);
        row.put("date", ev.date);
        row.put("from", "$" + (ev.priceCents / 100));
        row.put("status", new LkStatusDot(ev.tone, ev.status));
        LkBtn reserve = new LkBtn("Reserve")
                .variant(LkBtn.Variant.primary)
                .size(LkBtn.Size.s)
                .onClick(e -> UI.getCurrent().navigate("events/" + ev.id));
        row.put("act", reserve);
        grid.row(row);
    }

    private Component buildEmptyState() {
        Span empty = new Span("No events match your filters. Try widening category, region, dates, or price.");
        empty.getStyle()
                .set("display", "block").set("padding", "48px").set("text-align", "center")
                .set("color", "var(--muted)").set("background", "#fff")
                .set("border", "1px dashed var(--border-strong)").set("border-radius", "12px");
        return empty;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
